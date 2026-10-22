import json
import logging
import os
from typing import Any

import requests
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from ollama import Client
from pydantic import BaseModel


app = FastAPI(title="MiniCommerce AI Product Search Assistant")
logger = logging.getLogger(__name__)


# ============================================================
# CONFIGURAZIONE
# ============================================================

OLLAMA_HOST = os.getenv(
    "OLLAMA_HOST",
    "http://localhost:11434"
)

OLLAMA_MODEL = os.getenv(
    "OLLAMA_MODEL",
    os.getenv("MODEL_NAME", "qwen3:1.7b")
)

PRODUCT_SERVICE_URL = os.getenv(
    "PRODUCT_SERVICE_URL",
    "http://localhost:9002"
).rstrip("/")

# Nome del query parameter con cui il Product Service filtra per nome,
# es. GET /products?name=carne. Cambialo se il tuo servizio ne usa un altro.
PRODUCT_SEARCH_PARAM = os.getenv("PRODUCT_SEARCH_PARAM", "name")

# Se True, Python scarica /products UNA volta e tiene solo i prodotti il cui
# name o description contiene uno dei termini cercati. Serve finché il
# Product Service non filtra da solo. Quando lo farà, imposta LOCAL_FILTER=false
# (e PRODUCT_SEARCH_PARAM) così la ricerca passa interamente al microservizio.
LOCAL_FILTER = os.getenv("LOCAL_FILTER", "true").lower() == "true"

REQUEST_TIMEOUT = 20
MAX_SEARCH_TERMS = 5
MAX_ITEMS = 5

# Il frontend chiama questo servizio direttamente dal browser, quindi serve
# CORS. "*" va bene in sviluppo; in produzione usa gli origin reali,
# es. CORS_ORIGINS=http://localhost:3000,https://mio-sito.it
CORS_ORIGINS = [
    origin.strip()
    for origin in os.getenv("CORS_ORIGINS", "*").split(",")
    if origin.strip()
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"]
)

ollama_client = Client(host=OLLAMA_HOST)


class ProductServiceError(RuntimeError):
    """Il Product Service non è raggiungibile o risponde in modo non valido."""


# ============================================================
# REQUEST MODEL
# ============================================================

class ChatRequest(BaseModel):
    message: str


# ============================================================
# PRODUCT SERVICE
# ============================================================

def fetch_products(term: str | None = None) -> list[dict[str, Any]]:
    """
    GET /products. Con `term` passa il filtro al servizio
    (es. /products?name=pollo); senza, restituisce l'intero catalogo.
    """

    try:
        response = requests.get(
            f"{PRODUCT_SERVICE_URL}/products",
            params={PRODUCT_SEARCH_PARAM: term} if term else None,
            timeout=REQUEST_TIMEOUT
        )

        # Nessun risultato.
        if response.status_code == 404:
            return []

        response.raise_for_status()
        items = response.json()

    except requests.exceptions.RequestException as exc:
        raise ProductServiceError(
            f"Product Service non raggiungibile: {exc}"
        ) from exc

    except ValueError as exc:
        raise ProductServiceError(
            "Il Product Service non ha restituito un JSON valido."
        ) from exc

    if items is None:
        return []

    if not isinstance(items, list):
        raise ProductServiceError(
            "Il Product Service non ha restituito una lista."
        )

    return [item for item in items if isinstance(item, dict)]


def matches_term(product: dict[str, Any], term: str) -> bool:
    """Il termine compare nel name o nella description del prodotto."""

    needle = term.casefold()

    return any(
        needle in str(product.get(field) or "").casefold()
        for field in ("name", "description")
    )


def product_key(product: dict[str, Any]) -> Any:
    key = product.get("productId")

    if key is None:
        key = json.dumps(product, sort_keys=True, default=str)

    return key


def with_request_checks(
        product: dict[str, Any],
        requested_quantity: int | None,
        max_price: float | None
) -> dict[str, Any]:
    """
    Copia del prodotto con l'esito dei controlli sulla richiesta.
    L'LLM estrae soltanto i numeri: i confronti con stock e prezzo
    li fa Python, perché sono aritmetica esatta.

    Campi aggiunti (None se l'utente non ha indicato nulla da controllare):
      requestedQuantity  unità richieste
      quantityAvailable  True/False: lo stock copre la quantità richiesta
      missingUnits       unità che mancano per coprire la richiesta
      requestedMaxPrice  prezzo massimo per SINGOLO prodotto
      withinBudget       True/False: il prezzo unitario è <= al massimo
    """

    checked = dict(product)

    checked["requestedQuantity"] = requested_quantity
    checked["quantityAvailable"] = None
    checked["missingUnits"] = None
    checked["requestedMaxPrice"] = max_price
    checked["withinBudget"] = None

    if requested_quantity is not None:
        try:
            stock = int(product.get("quantity"))
        except (TypeError, ValueError):
            pass
        else:
            checked["quantityAvailable"] = stock >= requested_quantity
            checked["missingUnits"] = max(0, requested_quantity - stock)

    if max_price is not None:
        try:
            price = float(product.get("price"))
        except (TypeError, ValueError):
            pass
        else:
            checked["withinBudget"] = price <= max_price

    return checked


def failed_checks(product: dict[str, Any]) -> int:
    """Quanti controlli (quantità, budget) il prodotto non supera."""

    return (
            (product["quantityAvailable"] is False)
            + (product["withinBudget"] is False)
    )


def search_products(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """
    Cerca i prodotti di ogni richiesta e ne controlla quantità e budget.
    Restituisce tutti i prodotti trovati, senza duplicati, anche se la
    quantità non è disponibile o il prezzo supera il budget: i controlli
    sono un'informazione, non un filtro.
    """

    # Con il filtro locale basta una sola chiamata al servizio.
    catalog = fetch_products() if LOCAL_FILTER else None

    found: dict[Any, dict[str, Any]] = {}

    for item in items:
        if catalog is not None:
            matches = [
                product for product in catalog
                if any(
                    matches_term(product, term)
                    for term in item["searchTerms"]
                )
            ]
        else:
            matches = [
                product
                for term in item["searchTerms"]
                for product in fetch_products(term)
            ]

        batch: dict[Any, dict[str, Any]] = {}

        for product in matches:
            key = product_key(product)

            # Già trovato da una richiesta precedente: vale la prima.
            if key in found:
                continue

            batch.setdefault(
                key,
                with_request_checks(
                    product,
                    item["quantity"],
                    item["maxPrice"]
                )
            )

        # Prima i prodotti che superano tutti i controlli, poi quelli
        # che ne falliscono uno, poi due (l'ordine tra pari resta).
        found.update(
            sorted(
                batch.items(),
                key=lambda entry: failed_checks(entry[1])
            )
        )

    return list(found.values())


# ============================================================
# OUTPUT STRUTTURATO DEL MODELLO
# Ollama costringe il modello a rispondere con un JSON conforme
# a questo schema: non può "dimenticarsi" di estrarre i termini,
# cosa che i modelli piccoli fanno spesso con il tool calling.
# ============================================================

SEARCH_SCHEMA = {
    "type": "object",
    "properties": {
        "items": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "search_terms": {
                        "type": "array",
                        "items": {"type": "string"}
                    },
                    "quantity": {
                        "anyOf": [{"type": "integer"}, {"type": "null"}]
                    },
                    "max_price": {
                        "anyOf": [{"type": "number"}, {"type": "null"}]
                    }
                },
                "required": ["search_terms", "quantity", "max_price"]
            }
        }
    },
    "required": ["items"]
}


# ============================================================
# SYSTEM PROMPT
# ============================================================

SYSTEM_PROMPT = """
Sei il modulo di comprensione delle richieste di MiniCommerce.
Leggi il messaggio dell'utente ed estrai cosa vuole comprare.
Rispondi SOLO con un JSON di questa forma:

{"items": [{"search_terms": [...], "quantity": <intero o null>, "max_price": <numero o null>}]}

REGOLE:

1. Ogni prodotto diverso richiesto è un elemento di items, con la
   propria quantity.
2. search_terms contiene il nome di quel prodotto: parole chiave
   brevi, al singolare, in minuscolo. Mai saluti, quantità,
   unità di misura, pesi o prezzi.
3. Aggiungi sempre 2-3 sinonimi o prodotti più specifici dello
   stesso tipo, così si trovano anche nomi simili.
4. quantity è il numero di unità volute di quel prodotto. Converti
   i numeri scritti a parole (tre = 3, una dozzina = 12, un paio = 2).
   "un", "una", "uno" davanti al prodotto = 1. Pesi e misure
   (1kg, 30kg) NON sono quantità. Se non è indicata, null.
5. max_price è il prezzo massimo del SINGOLO prodotto (es. "sotto i
   20 euro", "massimo 50 euro l'uno"). Non moltiplicarlo per la
   quantità. Se l'utente indica un budget totale complessivo, o non
   indica nessun prezzo, null.
6. Se il messaggio non nomina nessun prodotto (per esempio solo
   un saluto), items deve essere [].
7. Non inventare, non rispondere alla domanda, non scrivere altro.

ESEMPI:

Utente: ciao voglio carne 1kg
{"items": [{"search_terms": ["carne", "manzo", "pollo", "maiale"], "quantity": null, "max_price": null}]}

Utente: vorrei 3 tavoli e una lampada
{"items": [{"search_terms": ["tavolo", "scrivania", "tavolino"], "quantity": 3, "max_price": null}, {"search_terms": ["lampada", "lampadina", "abat-jour"], "quantity": 1, "max_price": null}]}

Utente: mi servono dodici cuffie sotto i 50 euro
{"items": [{"search_terms": ["cuffia", "auricolare", "cuffie"], "quantity": 12, "max_price": 50}]}

Utente: ciao
{"items": []}
"""


# ============================================================
# PARSING DELL'OUTPUT DEL MODELLO
# I modelli piccoli sbagliano spesso i tipi: normalizziamo.
# ============================================================

def parse_search_terms(raw: Any) -> list[str]:
    if isinstance(raw, str):
        raw = [raw]

    if not isinstance(raw, list):
        return []

    terms: list[str] = []
    seen: set[str] = set()

    for item in raw:
        if not isinstance(item, str):
            continue

        term = item.strip()

        if term and term.lower() not in seen:
            seen.add(term.lower())
            terms.append(term)

    return terms


def parse_positive_number(raw: Any, cast: type) -> Any:
    if raw is None or isinstance(raw, bool):
        return None

    try:
        value = cast(raw)
    except (TypeError, ValueError):
        return None

    return value if value > 0 else None


def parse_items(raw: Any) -> list[dict[str, Any]]:
    # Un modello distratto può restituire un solo oggetto invece di una lista.
    if isinstance(raw, dict):
        raw = [raw]

    if not isinstance(raw, list):
        return []

    items: list[dict[str, Any]] = []

    for entry in raw:
        if not isinstance(entry, dict):
            continue

        terms = parse_search_terms(
            entry.get("search_terms")
        )[:MAX_SEARCH_TERMS]

        if not terms:
            continue

        items.append({
            "searchTerms": terms,
            "quantity": parse_positive_number(entry.get("quantity"), int),
            "maxPrice": parse_positive_number(entry.get("max_price"), float)
        })

    return items[:MAX_ITEMS]


# ============================================================
# RISPOSTA: sempre un JSON con la stessa struttura
# ============================================================

def flatten_terms(items: list[dict[str, Any]]) -> list[str]:
    terms: list[str] = []

    for item in items:
        for term in item["searchTerms"]:
            if term.lower() not in {t.lower() for t in terms}:
                terms.append(term)

    return terms


def build_response(
        products: list[dict[str, Any]],
        items: list[dict[str, Any]],
        message: str | None = None
) -> dict[str, Any]:
    return {
        "count": len(products),
        "request": {
            "searchTerms": flatten_terms(items),
            "items": items
        },
        "products": products,
        "message": message
    }


# ============================================================
# AGENT
# LLM ESTRAE PRODOTTI E QUANTITÀ -> PYTHON CERCA E CONFRONTA LO STOCK
# -> JSON
#
# Il JSON finale lo costruisce Python con i dati reali del servizio,
# non l'LLM: così il formato è garantito e nessun campo viene
# inventato o perso. Serve una sola chiamata al modello.
# ============================================================

def extract_items(user_message: str) -> list[dict[str, Any]]:
    response = ollama_client.chat(
        model=OLLAMA_MODEL,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_message}
        ],
        format=SEARCH_SCHEMA,
        think=False,
        options={
            "temperature": 0,
            "num_ctx": 4096,
            "num_predict": 512
        },
        keep_alive="10m"
    )

    content = (response.message.content or "").strip()

    logger.info("Output del modello: %s", content)

    try:
        data = json.loads(content)
    except ValueError:
        logger.warning("Il modello non ha restituito un JSON valido: %r", content)
        return []

    if not isinstance(data, dict):
        return []

    return parse_items(data.get("items"))


def run_agent(user_message: str) -> dict[str, Any]:
    items = extract_items(user_message)

    if not items:
        return build_response(
            products=[],
            items=[],
            message=(
                "Non ho capito quale prodotto cerchi. "
                "Puoi indicarmi il nome del prodotto?"
            )
        )

    products = search_products(items)

    return build_response(
        products=products,
        items=items,
        message=(
            None if products
            else "Nessun prodotto trovato per: "
                 f"{', '.join(flatten_terms(items))}."
        )
    )


# ============================================================
# CHAT ENDPOINT
# ============================================================

@app.post("/api/ai/chat")
def chat_endpoint(request: ChatRequest) -> dict[str, Any]:
    message = request.message.strip()

    if not message:
        raise HTTPException(
            status_code=422,
            detail="Il messaggio non può essere vuoto."
        )

    try:
        return run_agent(message)

    except ProductServiceError as exc:
        logger.exception("Errore del Product Service")

        raise HTTPException(
            status_code=503,
            detail="Il Product Service non è raggiungibile o non risponde correttamente."
        ) from exc

    except Exception as exc:
        logger.exception("Errore durante la richiesta AI")

        raise HTTPException(
            status_code=502,
            detail=(
                "Errore durante l'elaborazione della richiesta AI. "
                "Controlla Ollama e i log del servizio."
            )
        ) from exc


# ============================================================
# HEALTH CHECK
# ============================================================

@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "ai-service",
        "model": OLLAMA_MODEL
    }