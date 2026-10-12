
import json
import logging
import os
from typing import Any

import requests
from fastapi import FastAPI, HTTPException
from ollama import Client
from pydantic import BaseModel


app = FastAPI(title="MiniCommerce AI Order Assistant")
logger = logging.getLogger(__name__)


# ============================================================
# CONFIGURATION
# ============================================================

OLLAMA_HOST = os.getenv(
    "OLLAMA_HOST",
    "http://localhost:11434"
)

OLLAMA_MODEL = os.getenv(
    "OLLAMA_MODEL",
    os.getenv("MODEL_NAME", "qwen3:4b")
)

PRODUCT_SERVICE_URL = os.getenv(
    "PRODUCT_SERVICE_URL",
    "http://localhost:9002"
).rstrip("/")

PRODUCT_TIMEOUT = 20
MAX_TOOL_ROUNDS = 5

ollama_client = Client(host=OLLAMA_HOST)


# ============================================================
# REQUEST MODEL
# ============================================================

class ChatRequest(BaseModel):
    message: str


# ============================================================
# PRODUCT SERVICE
# ============================================================

def get_all_products() -> list[dict[str, Any]]:
    """Retrieve all products from the Product Service."""

    try:
        response = requests.get(
            f"{PRODUCT_SERVICE_URL}/products",
            timeout=PRODUCT_TIMEOUT
        )
        response.raise_for_status()

        products = response.json()

        if not isinstance(products, list):
            raise RuntimeError(
                "Il Product Service non ha restituito una lista."
            )

        return products

    except requests.exceptions.RequestException as exc:
        raise RuntimeError(
            f"Product Service non raggiungibile: {exc}"
        ) from exc


def get_product(product_id: str) -> dict[str, Any] | None:
    """Retrieve a product by its productId."""

    try:
        response = requests.get(
            f"{PRODUCT_SERVICE_URL}/products/{product_id}",
            timeout=PRODUCT_TIMEOUT
        )

        if response.status_code == 404:
            return None

        response.raise_for_status()
        product = response.json()

        # Il servizio può rispondere HTTP 200 con JSON null.
        if product is None:
            return None

        if not isinstance(product, dict):
            raise RuntimeError(
                "Il Product Service ha restituito un prodotto non valido."
            )

        return product

    except requests.exceptions.RequestException as exc:
        raise RuntimeError(
            f"Product Service non raggiungibile: {exc}"
        ) from exc


# ============================================================
# TOOL 1: SEARCH PRODUCTS
# ============================================================

def search_products(query: str) -> dict[str, Any]:
    """
    Search the MiniCommerce product catalog.

    Args:
        query: Product identifier, name or description to search for.
    """

    query = query.strip()

    if not query:
        return {
            "query": query,
            "products": [],
            "count": 0
        }

    products = get_all_products()
    query_lower = query.lower()
    matches = []

    for product in products:
        product_id = str(product.get("productId") or "")
        name = str(product.get("name") or "")
        description = str(product.get("description") or "")

        if any(
                query_lower in field.lower()
                for field in (product_id, name, description)
        ):
            matches.append({
                "productId": product.get("productId"),
                "name": product.get("name"),
                "description": product.get("description"),
                "price": product.get("price"),
                "quantity": product.get("quantity")
            })

    return {
        "query": query,
        "products": matches,
        "count": len(matches)
    }


# ============================================================
# TOOL 2: CHECK STOCK
# ============================================================

def check_stock(
        product_id: str,
        quantity: int
) -> dict[str, Any]:
    """
    Verify product existence and requested stock availability.

    Args:
        product_id: Exact product identifier from the catalog.
        quantity: Number of units requested by the user.
    """

    product_id = product_id.strip()

    if not product_id:
        return {
            "error": "product_id è obbligatorio."
        }

    if quantity <= 0:
        return {
            "productId": product_id,
            "requestedQuantity": quantity,
            "exists": None,
            "available": False,
            "reason": "La quantità deve essere maggiore di zero."
        }

    product = get_product(product_id)

    if product is None:
        return {
            "productId": product_id,
            "requestedQuantity": quantity,
            "exists": False,
            "available": False,
            "reason": "Prodotto non trovato nel catalogo."
        }

    try:
        if product.get("quantity") is None:
            raise ValueError("Quantità assente")

        available_quantity = int(product["quantity"])

    except (TypeError, ValueError) as exc:
        raise RuntimeError(
            f"La quantità disponibile per {product_id} non è valida."
        ) from exc

    return {
        "productId": product.get("productId", product_id),
        "name": product.get("name"),
        "price": product.get("price"),
        "exists": True,
        "requestedQuantity": quantity,
        "availableQuantity": available_quantity,
        "available": available_quantity >= quantity
    }


# ============================================================
# TOOL SCHEMAS FOR OLLAMA
# ============================================================

tools = [
    {
        "type": "function",
        "function": {
            "name": "search_products",
            "description": (
                "Search the real MiniCommerce catalog by product ID, "
                "name or description. Use this tool when the user asks "
                "to find a product, mentions a product ID without a "
                "quantity, or when product existence must be verified. "
                "Do not invent catalog results."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": (
                            "The product ID, name or description "
                            "to search for."
                        )
                    }
                },
                "required": ["query"],
                "additionalProperties": False
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "check_stock",
            "description": (
                "Check real product existence and stock availability. "
                "Use this tool whenever the user asks whether a specific "
                "quantity of a product is available or indicates an "
                "intention to purchase a known product and quantity. "
                "Never answer a stock question without this tool result."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "product_id": {
                        "type": "string",
                        "description": (
                            "Exact product ID returned by the catalog, "
                            "for example PROD-O15."
                        )
                    },
                    "quantity": {
                        "type": "integer",
                        "description": "Number of units requested."
                    }
                },
                "required": ["product_id", "quantity"],
                "additionalProperties": False
            }
        }
    }
]


# ============================================================
# SYSTEM PROMPT
# ============================================================

SYSTEM_PROMPT = """
Sei l'AI Shopping Assistant di MiniCommerce.
Interpreti i messaggi degli utenti e scegli quali strumenti utilizzare.
Rispondi sempre in italiano.

GESTIONE DELLE RICHIESTE:

1. Interpreta il significato del messaggio in linguaggio naturale.
   Non richiedere un formato specifico per gli identificativi
   o per le quantità.

2. Se l'utente cerca un prodotto, invoca search_products.

3. Se l'utente scrive soltanto un identificativo prodotto,
   invoca search_products per verificare se esiste.
   Se esiste, presenta le informazioni ottenute e chiedi quante
   unità desidera. Non presumere che voglia acquistarlo.

4. Se l'utente indica un prodotto e una quantità e domanda se
   sono disponibili, invoca check_stock prima di rispondere.

5. Se il nome o l'identificativo non identifica chiaramente
   un prodotto, usa search_products oppure chiedi chiarimenti.

6. Non inventare prodotti, identificativi, prezzi o quantità.
   I dati reali devono provenire dai risultati degli strumenti.

7. Non mostrare ragionamenti interni, bozze o monologhi.
   Dopo l'esecuzione degli strumenti, formula direttamente
   una risposta breve e naturale.

8. Se un tool segnala che un prodotto non esiste, comunica
   chiaramente il risultato.

9. Se la quantità richiesta supera quella disponibile, indica
   la quantità effettivamente disponibile e chiedi se l'utente
   desidera modificare la richiesta.

10. Non creare ordini, non effettuare pagamenti e non modificare
    direttamente il database.

GESTIONE DEL CARRELLO:

Se check_stock conferma che la quantità richiesta è disponibile,
puoi proporre al frontend l'azione usando esattamente:

ACTION: ADD_TO_CART
PRODUCT_ID: <productId>
QUANTITY: <quantity>

Usa esclusivamente i valori verificati dal tool.
Non emettere l'azione se il prodotto o la quantità non sono stati
verificati, oppure se la disponibilità è insufficiente.

Non dichiarare mai che il prodotto sia già stato aggiunto al carrello.
L'azione è soltanto una proposta per il frontend.
"""


# ============================================================
# TOOL EXECUTION
# Python executes tool calls selected by the LLM.
# It does not classify or parse the user's original message.
# ============================================================

def execute_tool(
        tool_name: str,
        arguments: dict[str, Any]
) -> dict[str, Any]:
    """Execute a tool requested by the model."""

    try:
        if tool_name == "search_products":
            query = arguments.get("query", "")

            if not isinstance(query, str):
                return {
                    "error": "query deve essere una stringa."
                }

            return search_products(query)

        if tool_name == "check_stock":
            product_id = (
                    arguments.get("product_id")
                    or arguments.get("productId")
            )
            quantity = arguments.get("quantity")

            if (
                    not isinstance(product_id, str)
                    or not product_id.strip()
            ):
                return {
                    "error": "product_id deve essere una stringa valida."
                }

            if (
                    isinstance(quantity, bool)
                    or not isinstance(quantity, int)
            ):
                return {
                    "error": "quantity deve essere un numero intero."
                }

            return check_stock(
                product_id.strip(),
                quantity
            )

        return {
            "error": f"Tool sconosciuto: {tool_name}"
        }

    except RuntimeError as exc:
        logger.exception(
            "Errore durante l'esecuzione del tool %s",
            tool_name
        )

        return {
            "error": str(exc),
            "tool": tool_name
        }


# ============================================================
# CHAT ENDPOINT
# LLM DECIDES -> PYTHON EXECUTES -> LLM RESPONDS
# ============================================================

@app.post("/api/ai/chat")
def chat_endpoint(request: ChatRequest):
    message = request.message.strip()

    if not message:
        raise HTTPException(
            status_code=422,
            detail="Il messaggio non può essere vuoto."
        )

    # La lista contiene messaggi utente/sistema e oggetti Message
    # restituiti dall'SDK Ollama.
    messages: list[Any] = [
        {
            "role": "system",
            "content": SYSTEM_PROMPT
        },
        {
            "role": "user",
            "content": message
        }
    ]

    try:
        # Ciclo agentico:
        # il modello può richiedere strumenti in più passaggi.
        for _ in range(MAX_TOOL_ROUNDS + 1):

            response = ollama_client.chat(
                model=OLLAMA_MODEL,
                messages=messages,
                tools=tools,
                think=True,
                options={
                    "num_ctx": 4096,
                    "num_predict": 512,
                    "temperature": 0
                },
                keep_alive="10m"
            )

            assistant_message = response.message
            tool_calls = assistant_message.tool_calls or []

            # Nessuna chiamata a tool:
            # il modello ha concluso la risposta.
            if not tool_calls:
                content = (
                        assistant_message.content or ""
                ).strip()

                # Il campo thinking non viene restituito all'utente.
                if not content:
                    logger.warning(
                        "Ollama non ha prodotto contenuto finale. "
                        "Thinking presente: %s",
                        bool(assistant_message.thinking)
                    )

                    return {
                        "response": (
                            "Non sono riuscito a completare la risposta. "
                            "Puoi riformulare la richiesta?"
                        )
                    }

                return {
                    "response": content
                }

            # Salva il messaggio dell'assistente con le chiamate
            # strutturate così come raccomandato dall'SDK Ollama.
            messages.append(assistant_message)

            # Esegue esclusivamente le funzioni invocate dal modello.
            for tool_call in tool_calls:
                function = tool_call.function
                arguments = dict(function.arguments or {})

                result = execute_tool(
                    function.name,
                    arguments
                )

                logger.info(
                    "Tool richiesto da Ollama: %s",
                    function.name
                )

                # Il risultato viene rimandato al modello.
                messages.append({
                    "role": "tool",
                    "tool_name": function.name,
                    "content": json.dumps(
                        result,
                        ensure_ascii=False,
                        default=str
                    )
                })

            # Si ripete il ciclo: Ollama può usare i risultati,
            # chiamare altri strumenti oppure formulare la risposta.

        raise HTTPException(
            status_code=502,
            detail=(
                "Ollama ha superato il numero massimo di passaggi "
                "con gli strumenti senza completare la risposta."
            )
        )

    except HTTPException:
        raise

    except Exception as exc:
        logger.exception(
            "Errore durante la richiesta AI"
        )

        raise HTTPException(
            status_code=502,
            detail=(
                "Errore durante l'elaborazione della richiesta AI. "
                "Verifica Ollama e i log del servizio."
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
