const GATEWAY_SERVICE_URL = "http://localhost:8080";

// Servizio AI (FastAPI). Se il gateway inoltra /api/ai/**, puoi
// sostituire con GATEWAY_SERVICE_URL.
const AI_SERVICE_URL = "http://localhost:9004";

// Il primo messaggio può essere lento: Ollama deve caricare il modello.
const AI_TIMEOUT_MS = 60000;

let products = [];
let cart = [];

// null = catalogo completo; altrimenti Set di productId trovati dall'AI.
let searchFilter = null;

// productId -> { quantity, maxPrice } richiesti all'assistente.
let searchRequests = new Map();


/* ===========================
   CARICAMENTO PRODOTTI
=========================== */

async function loadProducts() {

    try {

        const response = await fetch(
            `${GATEWAY_SERVICE_URL}/products`
        );

        if (!response.ok) {

            throw new Error(
                `Errore caricamento prodotti: HTTP ${response.status}`
            );
        }

        products = await response.json();

        displayProducts();

    } catch (error) {

        console.error(
            "ERRORE CARICAMENTO PRODOTTI:",
            error
        );

        showMessage(
            `Errore nel caricamento dei prodotti: ${error.message}`
        );
    }
}


/* ===========================
   CATALOGO
=========================== */

function displayProducts() {

    const container =
        document.getElementById("products");

    container.innerHTML = "";

    /*
     * Con una ricerca AI attiva mostra solo i prodotti trovati,
     * altrimenti l'intero catalogo.
     */
    const visibleProducts =
        searchFilter
            ? products.filter(
                p => searchFilter.has(p.productId)
            )
            : products;

    if (searchFilter && visibleProducts.length === 0) {

        container.innerHTML =
            "<p>Nessun prodotto trovato.</p>";

        return;
    }

    visibleProducts.forEach(product => {

        const card =
            document.createElement("div");

        card.className = "product-card";

        card.innerHTML = `
            <h3>${product.productId}</h3>

            <p>
                <strong>
                    ${product.name || "Product"}
                </strong>
            </p>

            <p>
                ${product.description || ""}
            </p>

            <p>
                Prezzo: €${Number(product.price).toFixed(2)}
            </p>

            <p>
                Disponibilità: ${product.quantity}
            </p>

            ${requestNotes(product)}

            <button
                onclick="addToCart('${product.productId}')"
                ${product.quantity === 0 ? "disabled" : ""}>
                ${
                    product.quantity === 0
                        ? "Esaurito"
                        : "Aggiungi"
                }
            </button>
        `;

        container.appendChild(card);
    });
}


/* ===========================
   RICERCA CON ASSISTENTE AI
=========================== */

function setupAiSearch() {

    const style =
        document.createElement("style");

    style.textContent = `
        .ai-search {
            margin-bottom: 1.5rem;
        }

        .ai-search-form {
            display: flex;
            flex-wrap: wrap;
            gap: 0.5rem;
        }

        .ai-search-form input {
            flex: 1 1 260px;
            padding: 0.6rem 0.8rem;
            font-size: 1rem;
            border: 1px solid #bbb;
            border-radius: 6px;
        }

        .ai-search-form button {
            padding: 0.6rem 1rem;
            font-size: 1rem;
            border-radius: 6px;
            cursor: pointer;
        }

        .ai-search-form [hidden] {
            display: none;
        }

        .ai-search-status {
            min-height: 1.2em;
            margin: 0.5rem 0 0;
        }

        .request-ok {
            color: #1a7f37;
            font-weight: 600;
        }

        .request-warning {
            color: #b54708;
            font-weight: 600;
        }
    `;

    document.head.appendChild(style);

    const section =
        document.createElement("section");

    section.className = "ai-search";

    section.innerHTML = `
        <form id="aiSearchForm" class="ai-search-form">

            <input
                type="text"
                id="aiSearchInput"
                placeholder="Cosa cerchi? Es. voglio del pollo"
                maxlength="200"
                autocomplete="off">

            <button type="submit" id="aiSearchButton">
                Cerca con AI
            </button>

            <button type="button" id="aiResetButton" hidden>
                Mostra tutti
            </button>

        </form>

        <p
            id="aiSearchStatus"
            class="ai-search-status"
            role="status">
        </p>
    `;

    document
        .getElementById("products")
        .before(section);

    document
        .getElementById("aiSearchForm")
        .addEventListener("submit", event => {

            event.preventDefault();

            const message =
                document
                    .getElementById("aiSearchInput")
                    .value
                    .trim();

            if (message) {
                searchWithAI(message);
            }
        });

    document
        .getElementById("aiResetButton")
        .addEventListener("click", clearAiSearch);
}


function setAiStatus(text) {

    document
        .getElementById("aiSearchStatus")
        .textContent = text;
}


function setAiBusy(busy) {

    document
        .getElementById("aiSearchInput")
        .disabled = busy;

    const button =
        document.getElementById("aiSearchButton");

    button.disabled = busy;

    button.textContent =
        busy
            ? "Ricerca in corso..."
            : "Cerca con AI";
}


function toNumber(value) {

    if (value === null || value === undefined || value === "") {
        return null;
    }

    const number = Number(value);

    return Number.isNaN(number) ? null : number;
}


/*
 * Riassume un controllo (quantità o budget) sui prodotti trovati.
 * `field` vale true/false per ogni prodotto, null se non c'era
 * nulla da controllare.
 */
function summarizeCheck(foundProducts, field, texts) {

    const checked =
        foundProducts.filter(
            p => typeof p[field] === "boolean"
        );

    if (checked.length === 0) {
        return "";
    }

    const passed =
        checked.filter(p => p[field]).length;

    if (passed === checked.length) {

        return typeof texts.all === "function"
            ? ` ${texts.all(checked.length)}`
            : ` ${texts.all}`;
    }

    if (passed === 0) {
        return ` ${texts.none}`;
    }

    return ` ${passed} su ${checked.length} ${texts.some}`;
}


function buildAiStatus(foundProducts, searchTerms) {

    const terms =
        searchTerms.join(", ");

    if (foundProducts.length === 0) {

        return `Nessun prodotto trovato per: ${terms}.`;
    }

    let text =
        foundProducts.length === 1
            ? `Trovato 1 prodotto per: ${terms}.`
            : `Trovati ${foundProducts.length} prodotti per: ${terms}.`;

    // Riepilogo dei controlli fatti dal backend.
    text += summarizeCheck(
        foundProducts,
        "quantityAvailable",
        {
            all: "La quantità richiesta è disponibile.",
            none: "Nessun prodotto ha abbastanza scorte per la quantità richiesta.",
            some: "hanno abbastanza scorte per la quantità richiesta."
        }
    );

    text += summarizeCheck(
        foundProducts,
        "withinBudget",
        {
            all: n =>
                n === 1
                    ? "Rientra nel budget."
                    : "Tutti rientrano nel budget.",
            none: "Nessun prodotto rientra nel budget.",
            some: "rientrano nel budget."
        }
    );

    return text;
}


/*
 * Note mostrate sulla card: confrontano quantità e budget richiesti
 * con stock e prezzo ATTUALI, quindi restano corrette anche dopo
 * un aggiornamento del catalogo. Il budget è per singolo prodotto.
 */
function requestNotes(product) {

    const request =
        searchRequests.get(product.productId);

    if (!request) {
        return "";
    }

    let notes = "";

    const stock =
        toNumber(product.quantity);

    if (request.quantity && stock !== null) {

        const missing =
            request.quantity - stock;

        notes +=
            missing > 0
                ? `<p class="request-warning">⚠ Richiesti ${request.quantity}: ne mancano ${missing}</p>`
                : `<p class="request-ok">✓ Richiesti ${request.quantity}: disponibili</p>`;
    }

    const price =
        toNumber(product.price);

    if (request.maxPrice && price !== null) {

        const limit =
            request.maxPrice.toFixed(2);

        notes +=
            price > request.maxPrice
                ? `<p class="request-warning">⚠ Sopra il budget: €${price.toFixed(2)} (massimo €${limit})</p>`
                : `<p class="request-ok">✓ Nel budget (massimo €${limit})</p>`;
    }

    return notes;
}


async function searchWithAI(message) {

    const controller =
        new AbortController();

    const timeoutId =
        setTimeout(
            () => controller.abort(),
            AI_TIMEOUT_MS
        );

    setAiBusy(true);

    setAiStatus("L'assistente sta cercando...");

    try {

        const response =
            await fetch(
                `${AI_SERVICE_URL}/api/ai/chat`,
                {
                    method: "POST",

                    headers: {
                        "Content-Type": "application/json"
                    },

                    body: JSON.stringify({ message }),

                    signal: controller.signal
                }
            );

        if (!response.ok) {

            const errorBody =
                await response.json().catch(() => null);

            throw new Error(
                typeof errorBody?.detail === "string"
                    ? errorBody.detail
                    : `HTTP ${response.status}`
            );
        }

        const data =
            await response.json();

        console.log(
            "RISPOSTA ASSISTENTE AI:",
            data
        );

        const foundProducts =
            Array.isArray(data.products)
                ? data.products
                : [];

        const searchTerms =
            Array.isArray(data.request?.searchTerms)
                ? data.request.searchTerms
                : [];

        /*
         * L'assistente non ha capito cosa cercare (es. un saluto):
         * mostra il suo messaggio e lascia la vista com'è.
         */
        if (searchTerms.length === 0) {

            setAiStatus(
                data.message ||
                "Non ho capito quale prodotto cerchi."
            );

            return;
        }

        /*
         * I dati dell'AI sono i più freschi: aggiorna il catalogo
         * locale, così il carrello usa le stesse quantità.
         */
        foundProducts.forEach(found => {

            const index =
                products.findIndex(
                    p => p.productId === found.productId
                );

            if (index === -1) {
                products.push(found);
            } else {
                products[index] = found;
            }
        });

        searchFilter =
            new Set(
                foundProducts.map(p => p.productId)
            );

        searchRequests =
            new Map(
                foundProducts
                    .filter(
                        p => p.requestedQuantity || p.requestedMaxPrice
                    )
                    .map(p => [
                        p.productId,
                        {
                            quantity: p.requestedQuantity || null,
                            maxPrice: p.requestedMaxPrice || null
                        }
                    ])
            );

        displayProducts();

        document
            .getElementById("aiResetButton")
            .hidden = false;

        setAiStatus(
            buildAiStatus(
                foundProducts,
                searchTerms
            )
        );

    } catch (error) {

        console.error(
            "ERRORE RICERCA AI:",
            error
        );

        let reason = error.message;

        if (error.name === "AbortError") {

            reason =
                "l'assistente ha impiegato troppo tempo a rispondere.";

        } else if (error instanceof TypeError) {

            reason =
                `servizio AI non raggiungibile su ${AI_SERVICE_URL}. ` +
                "Controlla che sia avviato e che il CORS sia abilitato " +
                "(i dettagli sono nella console del browser).";
        }

        setAiStatus(
            `Errore assistente AI: ${reason}`
        );

    } finally {

        clearTimeout(timeoutId);

        setAiBusy(false);
    }
}


function clearAiSearch() {

    searchFilter = null;

    searchRequests = new Map();

    document
        .getElementById("aiSearchInput")
        .value = "";

    document
        .getElementById("aiResetButton")
        .hidden = true;

    setAiStatus("");

    displayProducts();
}


/* ===========================
   CARRELLO
=========================== */

function addToCart(productId) {

    const product =
        products.find(
            p => p.productId === productId
        );

    if (!product || product.quantity <= 0) {
        return;
    }

    const item =
        cart.find(
            p => p.productId === productId
        );

    if (item) {

        if (item.quantity >= product.quantity) {

            showMessage(
                `Disponibilità massima raggiunta per ${productId}.`
            );

            return;
        }

        item.quantity++;

    } else {

        cart.push({
            ...product,
            quantity: 1
        });
    }

    displayCart();
}


function increaseQuantity(productId) {

    const item =
        cart.find(
            p => p.productId === productId
        );

    const product =
        products.find(
            p => p.productId === productId
        );

    if (!item || !product) {
        return;
    }

    if (item.quantity >= product.quantity) {

        showMessage(
            `Disponibilità massima raggiunta per ${productId}.`
        );

        return;
    }

    item.quantity++;

    displayCart();
}


function decreaseQuantity(productId) {

    const item =
        cart.find(
            p => p.productId === productId
        );

    if (!item) {
        return;
    }

    item.quantity--;

    if (item.quantity <= 0) {

        cart =
            cart.filter(
                p => p.productId !== productId
            );
    }

    displayCart();
}


function displayCart() {

    const container =
        document.getElementById("cart");

    if (cart.length === 0) {

        container.innerHTML =
            "<p>Il carrello è vuoto.</p>";

        return;
    }

    const total =
        cart.reduce(
            (sum, item) =>
                sum + item.price * item.quantity,
            0
        );

    container.innerHTML = `
        ${cart.map(item => `

            <div class="cart-item">

                <div>
                    <strong>${item.productId}</strong><br>
                    €${Number(item.price).toFixed(2)}
                    × ${item.quantity}
                </div>

                <div class="quantity-controls">

                    <button
                        onclick="decreaseQuantity('${item.productId}')">
                        −
                    </button>

                    <span>${item.quantity}</span>

                    <button
                        onclick="increaseQuantity('${item.productId}')">
                        +
                    </button>

                </div>

            </div>

        `).join("")}

        <hr>

        <h3>
            Totale: €${total.toFixed(2)}
        </h3>
    `;
}


/* ===========================
   CONTROLLO STOCK
=========================== */

function checkCartAvailability() {

    for (const item of cart) {

        const product =
            products.find(
                p => p.productId === item.productId
            );

        if (!product) {

            showMessage(
                `Prodotto ${item.productId} non trovato.`
            );

            return false;
        }

        if (item.quantity > product.quantity) {

            showMessage(
                `Quantità non disponibile per ${item.productId}.`
            );

            return false;
        }
    }

    return true;
}


/* ===========================
   RECUPERO ORDINE
=========================== */

async function getOrder(orderId) {

    const response =
        await fetch(
            `${GATEWAY_SERVICE_URL}/orders/${orderId}`
        );

    if (!response.ok) {

        const errorText =
            await response.text();

        throw new Error(
            `Errore recupero ordine: HTTP ${response.status}: ${errorText}`
        );
    }

    return await response.json();
}


/* ===========================
   ATTESA RISULTATO SAGA
=========================== */

async function waitForOrderCompletion(
    orderId,
    maxAttempts = 30,
    delayMs = 1000
) {

    for (let attempt = 1; attempt <= maxAttempts; attempt++) {

        const savedOrder =
            await getOrder(orderId);

        console.log(
            `STATO ORDINE [${attempt}/${maxAttempts}]:`,
            savedOrder.status
        );

        if (
            savedOrder.status === "APPROVED" ||
            savedOrder.status === "REJECTED"
        ) {

            return savedOrder;
        }

        await new Promise(
            resolve =>
                setTimeout(resolve, delayMs)
        );
    }

    throw new Error(
        "Timeout: l'ordine è ancora PENDING dopo 30 secondi."
    );
}


/* ===========================
   ORDINE
=========================== */

async function placeOrder() {

    const orderButton =
        document.getElementById("orderButton");

    if (cart.length === 0) {

        showMessage(
            "Il carrello è vuoto."
        );

        return;
    }

    const customerId =
        document
            .getElementById("customerId")
            .value
            .trim();

    const cardNumber =
        document
            .getElementById("cardNumber")
            .value
            .trim();

    if (!customerId || !cardNumber) {

        showMessage(
            "Inserisci Customer ID e numero carta."
        );

        return;
    }

    if (!checkCartAvailability()) {
        return;
    }

    const total =
        cart.reduce(
            (sum, item) =>
                sum + item.price * item.quantity,
            0
        );

    const order = {

        orderId: crypto.randomUUID(),

        productIds:
            cart
                .flatMap(item =>
                    Array(item.quantity)
                        .fill(item.productId)
                )
                .join(","),

        customerId: customerId,

        creditCardNumber: cardNumber,

        totalPrice: total,

        status: "PENDING"
    };

    console.log(
        "ORDINE INVIATO:",
        order
    );

    /*
     * Evita doppi click mentre la Saga è in esecuzione.
     */
    orderButton.disabled = true;

    showMessage(
        "Ordine inviato. Elaborazione in corso..."
    );

    try {

        /* ===========================
           CREAZIONE ORDINE
        =========================== */

        const response =
            await fetch(
                `${GATEWAY_SERVICE_URL}/order`,
                {
                    method: "POST",

                    headers: {
                        "Content-Type": "application/json"
                    },

                    body: JSON.stringify(order)
                }
            );

        console.log(
            "RISPOSTA POST /order:",
            response.status
        );

        if (!response.ok) {

            const errorText =
                await response.text();

            console.error(
                "ERRORE HTTP CREAZIONE ORDINE:",
                errorText
            );

            throw new Error(
                `HTTP ${response.status}: ${errorText}`
            );
        }

        const result =
            await response.json();

        console.log(
            "RISPOSTA CREAZIONE ORDINE:",
            result
        );

        /* ===========================
           RECUPERO ORDER ID
        =========================== */

        const createdOrder =
            result.order || result;

        if (!createdOrder) {

            throw new Error(
                "La risposta del server è vuota."
            );
        }

        const orderId =
            createdOrder.orderId;

        if (!orderId) {

            throw new Error(
                "La risposta del server non contiene orderId."
            );
        }

        console.log(
            "ORDER ID:",
            orderId
        );

        /* ===========================
           STATO INIZIALE
        =========================== */

        console.log(
            "ORDINE INIZIALE:",
            createdOrder.status
        );

        showMessage(
            `Ordine ${orderId} ricevuto. Stato: PENDING...`
        );

        /* ===========================
           ATTESA SAGA
        =========================== */

        const savedOrder =
            await waitForOrderCompletion(
                orderId
            );

        console.log(
            "ORDINE FINALE:",
            savedOrder
        );

        /* ===========================
           RISULTATO FINALE
        =========================== */

        if (savedOrder.status === "APPROVED") {

            showMessage(
                `Ordine APPROVATO ✓ (${orderId})`
            );

            /*
             * Il carrello viene svuotato
             * solamente se l'ordine è stato approvato.
             */
            cart = [];

            displayCart();

        } else if (
            savedOrder.status === "REJECTED"
        ) {

            showMessage(
                `Ordine RIFIUTATO ✗ (${orderId})`
            );
        }

        /* ===========================
           AGGIORNA CATALOGO
        =========================== */

        await loadProducts();

        /* ===========================
           RESET FORM
        =========================== */

        document
            .getElementById("customerId")
            .value = "";

        document
            .getElementById("cardNumber")
            .value = "";

    } catch (error) {

        console.error(
            "ERRORE DURANTE LA CREAZIONE DELL'ORDINE:",
            error
        );

        showMessage(
            `Errore: ${error.message}`
        );

    } finally {

        orderButton.disabled = false;
    }
}


/* ===========================
   MESSAGGI
=========================== */

function showMessage(message) {

    document
        .getElementById("message")
        .textContent = message;
}


/* ===========================
   AVVIO
=========================== */

document
    .getElementById("orderButton")
    .addEventListener(
        "click",
        placeOrder
    );

setupAiSearch();

displayCart();

loadProducts();