const GATEWAY_SERVICE_URL = "http://localhost:8080";

let products = [];
let cart = [];


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

    products.forEach(product => {

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

displayCart();

loadProducts();