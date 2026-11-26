const state = {
    token: localStorage.getItem("driveease_token") || "",
    user: JSON.parse(localStorage.getItem("driveease_user") || "null"),
    vehicles: [],
    selectedVehicleId: null,
    bookingsMode: "my"
};

const todayIso = () => new Date().toISOString().slice(0, 10);
const addDaysIso = (days) => {
    const d = new Date();
    d.setDate(d.getDate() + days);
    return d.toISOString().slice(0, 10);
};

async function api(path, options = {}) {
    const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
    if (state.token) headers["Authorization"] = `Bearer ${state.token}`;
    const res = await fetch(path, { ...options, headers });
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) {
        throw new Error(data?.message || data?.error || `Request failed (${res.status})`);
    }
    return data;
}

function showToast(msg, ok = true) {
    const el = document.getElementById("toast");
    el.textContent = msg;
    el.className = `toast ${ok ? "ok" : "err"}`;
    clearTimeout(el._timer);
    el._timer = setTimeout(() => el.classList.add("hidden"), 5000);
}

function isManager() {
    return state.user && (state.user.role === "ADMIN" || state.user.role === "FLEET_MANAGER");
}

function renderAuth() {
    const bar = document.getElementById("authBar");
    document.querySelectorAll(".manager-only").forEach(el => {
        el.classList.toggle("hidden", !isManager());
    });
    if (state.user) {
        bar.innerHTML = `
            <span class="user-pill">${state.user.fullName} &bull; <strong>${state.user.role}</strong></span>
            <button class="btn btn-secondary" id="btnLogout">Sign Out</button>
        `;
        document.getElementById("btnLogout").onclick = () => {
            state.token = "";
            state.user = null;
            localStorage.removeItem("driveease_token");
            localStorage.removeItem("driveease_user");
            renderAuth();
            loadBookings();
            showToast("Signed out.");
        };
    } else {
        bar.innerHTML = `<button class="btn btn-primary" id="btnOpenLogin">Sign In / Register</button>`;
        document.getElementById("btnOpenLogin").onclick = () => {
            document.getElementById("authModal").classList.remove("hidden");
        };
    }
}

async function loadSummaryAndFleet() {
    try {
        const search = document.getElementById("filterSearch").value.trim();
        const cat = document.getElementById("filterCategory").value;
        const st = document.getElementById("filterStatus").value;
        const params = new URLSearchParams();
        if (search) params.set("search", search);
        if (cat) params.set("category", cat);
        if (st) params.set("status", st);

        const [summary, vehicles] = await Promise.all([
            api("/api/vehicles/summary"),
            api(`/api/vehicles?${params.toString()}`)
        ]);
        document.getElementById("kpiTotal").textContent = summary.total;
        document.getElementById("kpiAvailable").textContent = summary.available;
        document.getElementById("kpiRented").textContent = summary.rented;
        document.getElementById("kpiMaintenance").textContent = summary.maintenance;

        state.vehicles = vehicles;
        if (!state.selectedVehicleId && vehicles.length) {
            state.selectedVehicleId = vehicles[0].id;
        }
        renderFleet();
        populateVehicleSelects();
        if (state.selectedVehicleId) loadVehicleCalendar(state.selectedVehicleId);
    } catch (err) {
        showToast(err.message, false);
    }
}

function renderFleet() {
    const grid = document.getElementById("fleetGrid");
    if (!state.vehicles.length) {
        grid.innerHTML = `<div class="card muted">No vehicles match the active filters.</div>`;
        return;
    }
    grid.innerHTML = state.vehicles.map(v => `
        <article class="vehicle-card ${v.id === state.selectedVehicleId ? "selected" : ""}">
            <div class="v-head">
                <div>
                    <div class="v-title">${v.modelYear} ${v.make} ${v.model}</div>
                    <div class="v-meta">${v.registrationNumber} &bull; ${v.category} &bull; ${v.locationHub}</div>
                </div>
                <span class="badge badge-${v.status}">${v.status}</span>
            </div>
            <div class="v-meta">Mileage: ${v.mileage.toLocaleString()} km &bull; Fuel/Charge: ${v.fuelLevelPercent}%</div>
            <div class="v-head">
                <span class="v-rate">$${Number(v.dailyRate).toFixed(2)}/day</span>
                <div class="btn-row">
                    <button class="btn btn-secondary btn-sm" onclick="selectVehicle(${v.id})">Select</button>
                    ${isManager() ? `<button class="btn btn-secondary btn-sm" onclick="toggleMaint(${v.id}, '${v.status}')">${v.status === "MAINTENANCE" ? "Restore" : "Maint."}</button>` : ""}
                </div>
            </div>
        </article>
    `).join("");
}

function populateVehicleSelects() {
    const opts = state.vehicles.map(v =>
        `<option value="${v.id}" ${v.id === state.selectedVehicleId ? "selected" : ""}>${v.modelYear} ${v.make} ${v.model} (${v.registrationNumber}) - $${v.dailyRate}/day [${v.status}]</option>`
    ).join("");
    document.getElementById("bookVehicleId").innerHTML = opts;
    document.getElementById("mVehicleId").innerHTML = opts;
    updateQuote();
}

window.selectVehicle = (id) => {
    state.selectedVehicleId = id;
    document.getElementById("bookVehicleId").value = id;
    renderFleet();
    updateQuote();
    loadVehicleCalendar(id);
};

window.toggleMaint = async (id, currentStatus) => {
    const nextStatus = currentStatus === "MAINTENANCE" ? "AVAILABLE" : "MAINTENANCE";
    try {
        await api(`/api/vehicles/${id}/status`, {
            method: "PATCH",
            body: JSON.stringify({ status: nextStatus })
        });
        showToast(`Vehicle #${id} status updated to ${nextStatus}.`);
        loadSummaryAndFleet();
    } catch (err) {
        showToast(err.message, false);
    }
};

function updateQuote() {
    const vid = Number(document.getElementById("bookVehicleId").value);
    const s = document.getElementById("bookStartDate").value;
    const e = document.getElementById("bookEndDate").value;
    const v = state.vehicles.find(x => x.id === vid);
    const box = document.getElementById("quoteBox");
    if (!v || !s || !e) return;
    const days = Math.floor((new Date(e) - new Date(s)) / 86400000) + 1;
    if (days < 1) {
        box.textContent = "End date must be on or after start date.";
        return;
    }
    const total = (days * Number(v.dailyRate)).toFixed(2);
    box.innerHTML = `<strong>${days} day(s)</strong> &times; $${Number(v.dailyRate).toFixed(2)} = <strong>$${total}</strong>`;
}

async function loadVehicleCalendar(vehicleId) {
    try {
        const slots = await api(`/api/bookings/vehicle/${vehicleId}/calendar`);
        const ul = document.getElementById("calendarList");
        if (!slots.length) {
            ul.innerHTML = `<li class="muted">No active reservations. All upcoming dates open.</li>`;
            return;
        }
        ul.innerHTML = slots.map(s =>
            `<li>#${s.bookingId}: <strong>${s.startDate}</strong> &rarr; <strong>${s.endDate}</strong> (${s.status})</li>`
        ).join("");
    } catch {
        // ignore if booking service offline
    }
}

async function loadBookings() {
    const body = document.getElementById("bookingsBody");
    if (!state.token) {
        body.innerHTML = `<tr><td colspan="7" class="muted">Sign in to view and manage reservations.</td></tr>`;
        return;
    }
    try {
        const endpoint = (state.bookingsMode === "all" && isManager()) ? "/api/bookings" : "/api/bookings/my";
        document.getElementById("bookingsHeading").textContent =
            endpoint === "/api/bookings" ? "All Fleet Reservations" : "My Reservations";
        const rows = await api(endpoint);
        if (!rows.length) {
            body.innerHTML = `<tr><td colspan="7" class="muted">No reservations found.</td></tr>`;
            return;
        }
        body.innerHTML = rows.map(b => `
            <tr>
                <td>#${b.id}</td>
                <td>${b.vehicleSummary}</td>
                <td>${b.customerName}<br><span class="muted">${b.customerEmail}</span></td>
                <td>${b.startDate} &rarr; ${b.endDate}${b.actualReturnDate ? `<br><span class="muted">Returned: ${b.actualReturnDate}</span>` : ""}</td>
                <td>$${Number(b.totalCost).toFixed(2)}${Number(b.lateFee) > 0 ? `<br><span class="muted">Late fee: $${Number(b.lateFee).toFixed(2)}</span>` : ""}</td>
                <td><span class="badge badge-${b.status}">${b.status}</span></td>
                <td>
                    <div class="btn-row">
                        ${b.status === "CONFIRMED" ? `<button class="btn btn-primary btn-sm" onclick="pickupBooking(${b.id})">Pickup</button>` : ""}
                        ${(b.status === "ACTIVE" || b.status === "CONFIRMED") ? `<button class="btn btn-secondary btn-sm" onclick="openReturn(${b.id}, ${b.vehicleId})">Return</button>` : ""}
                        ${b.status === "CONFIRMED" ? `<button class="btn btn-danger btn-sm" onclick="cancelBooking(${b.id})">Cancel</button>` : ""}
                    </div>
                </td>
            </tr>
        `).join("");
    } catch (err) {
        showToast(err.message, false);
    }
}

window.pickupBooking = async (id) => {
    try {
        await api(`/api/bookings/${id}/pickup`, { method: "POST" });
        showToast(`Booking #${id} picked up — vehicle marked RENTED.`);
        loadBookings();
        loadSummaryAndFleet();
    } catch (err) {
        showToast(err.message, false);
    }
};

window.cancelBooking = async (id) => {
    try {
        await api(`/api/bookings/${id}/cancel`, { method: "POST" });
        showToast(`Booking #${id} cancelled.`);
        loadBookings();
        loadVehicleCalendar(state.selectedVehicleId);
    } catch (err) {
        showToast(err.message, false);
    }
};

window.openReturn = (bookingId, vehicleId) => {
    const v = state.vehicles.find(x => x.id === vehicleId);
    document.getElementById("retBookingId").value = bookingId;
    document.getElementById("retDate").value = todayIso();
    document.getElementById("retMileage").value = v ? v.mileage + 120 : 10000;
    document.getElementById("retFuel").value = 90;
    document.getElementById("retNotes").value = "";
    document.getElementById("retNeedsMaint").checked = false;
    document.getElementById("returnModal").classList.remove("hidden");
};

function bindEvents() {
    document.querySelectorAll(".tab[data-tab]").forEach(btn => {
        btn.onclick = () => {
            document.querySelectorAll(".tab[data-tab]").forEach(t => t.classList.remove("active"));
            document.querySelectorAll(".tab-panel").forEach(p => p.classList.remove("active"));
            btn.classList.add("active");
            document.getElementById(`tab-${btn.dataset.tab}`).classList.add("active");
            if (btn.dataset.tab === "bookings") loadBookings();
        };
    });

    document.getElementById("btnRefreshFleet").onclick = loadSummaryAndFleet;
    ["filterSearch", "filterCategory", "filterStatus"].forEach(id => {
        document.getElementById(id).addEventListener("input", loadSummaryAndFleet);
    });

    document.getElementById("bookVehicleId").onchange = (e) => {
        state.selectedVehicleId = Number(e.target.value);
        renderFleet();
        updateQuote();
        loadVehicleCalendar(state.selectedVehicleId);
    };
    ["bookStartDate", "bookEndDate"].forEach(id => {
        document.getElementById(id).onchange = updateQuote;
    });

    document.getElementById("btnCheckAvail").onclick = async () => {
        const vehicleId = document.getElementById("bookVehicleId").value;
        const startDate = document.getElementById("bookStartDate").value;
        const endDate = document.getElementById("bookEndDate").value;
        try {
            const res = await api(`/api/bookings/availability?vehicleId=${vehicleId}&startDate=${startDate}&endDate=${endDate}`);
            showToast(res.reason, res.available);
        } catch (err) {
            showToast(err.message, false);
        }
    };

    document.getElementById("bookingForm").onsubmit = async (e) => {
        e.preventDefault();
        if (!state.token) {
            document.getElementById("authModal").classList.remove("hidden");
            showToast("Please sign in to create a booking.", false);
            return;
        }
        try {
            const created = await api("/api/bookings", {
                method: "POST",
                body: JSON.stringify({
                    vehicleId: Number(document.getElementById("bookVehicleId").value),
                    startDate: document.getElementById("bookStartDate").value,
                    endDate: document.getElementById("bookEndDate").value,
                    notes: document.getElementById("bookNotes").value
                })
            });
            showToast(`Reservation #${created.id} confirmed! Total: $${Number(created.totalCost).toFixed(2)}`);
            loadVehicleCalendar(created.vehicleId);
            loadBookings();
        } catch (err) {
            showToast(err.message, false);
        }
    };

    document.getElementById("btnMyBookings").onclick = () => { state.bookingsMode = "my"; loadBookings(); };
    document.getElementById("btnAllBookings").onclick = () => { state.bookingsMode = "all"; loadBookings(); };

    document.getElementById("closeAuthModal").onclick = () => document.getElementById("authModal").classList.add("hidden");
    document.getElementById("closeReturnModal").onclick = () => document.getElementById("returnModal").classList.add("hidden");

    document.querySelectorAll(".demo-creds .chip").forEach(chip => {
        chip.onclick = () => {
            document.getElementById("loginEmail").value = chip.dataset.email;
            document.getElementById("loginPassword").value = chip.dataset.pass;
        };
    });

    document.getElementById("loginForm").onsubmit = async (e) => {
        e.preventDefault();
        try {
            const res = await api("/api/auth/login", {
                method: "POST",
                body: JSON.stringify({
                    email: document.getElementById("loginEmail").value,
                    password: document.getElementById("loginPassword").value
                })
            });
            state.token = res.token;
            state.user = res.user;
            localStorage.setItem("driveease_token", res.token);
            localStorage.setItem("driveease_user", JSON.stringify(res.user));
            document.getElementById("authModal").classList.add("hidden");
            renderAuth();
            renderFleet();
            loadBookings();
            showToast(`Welcome back, ${res.user.fullName}!`);
        } catch (err) {
            showToast(err.message, false);
        }
    };

    document.getElementById("registerForm").onsubmit = async (e) => {
        e.preventDefault();
        try {
            const res = await api("/api/auth/register", {
                method: "POST",
                body: JSON.stringify({
                    fullName: document.getElementById("regName").value,
                    email: document.getElementById("regEmail").value,
                    password: document.getElementById("regPassword").value,
                    driverLicenseNo: document.getElementById("regLicense").value
                })
            });
            state.token = res.token;
            state.user = res.user;
            localStorage.setItem("driveease_token", res.token);
            localStorage.setItem("driveease_user", JSON.stringify(res.user));
            document.getElementById("authModal").classList.add("hidden");
            renderAuth();
            renderFleet();
            loadBookings();
            showToast(`Account created for ${res.user.fullName}!`);
        } catch (err) {
            showToast(err.message, false);
        }
    };

    document.getElementById("returnForm").onsubmit = async (e) => {
        e.preventDefault();
        const id = document.getElementById("retBookingId").value;
        try {
            const res = await api(`/api/bookings/${id}/return`, {
                method: "POST",
                body: JSON.stringify({
                    actualReturnDate: document.getElementById("retDate").value,
                    returnMileage: Number(document.getElementById("retMileage").value),
                    returnFuelPercent: Number(document.getElementById("retFuel").value),
                    needsMaintenance: document.getElementById("retNeedsMaint").checked,
                    notes: document.getElementById("retNotes").value
                })
            });
            document.getElementById("returnModal").classList.add("hidden");
            showToast(`Booking #${id} returned. Late fee: $${Number(res.lateFee).toFixed(2)}`);
            loadBookings();
            loadSummaryAndFleet();
        } catch (err) {
            showToast(err.message, false);
        }
    };

    document.getElementById("addVehicleForm").onsubmit = async (e) => {
        e.preventDefault();
        try {
            const created = await api("/api/vehicles", {
                method: "POST",
                body: JSON.stringify({
                    vin: document.getElementById("vVin").value,
                    registrationNumber: document.getElementById("vReg").value,
                    make: document.getElementById("vMake").value,
                    model: document.getElementById("vModel").value,
                    modelYear: Number(document.getElementById("vYear").value),
                    category: document.getElementById("vCategory").value,
                    dailyRate: Number(document.getElementById("vRate").value),
                    mileage: Number(document.getElementById("vMileage").value),
                    fuelLevelPercent: Number(document.getElementById("vFuel").value),
                    locationHub: document.getElementById("vHub").value
                })
            });
            showToast(`Registered ${created.make} ${created.model} (${created.registrationNumber}).`);
            e.target.reset();
            loadSummaryAndFleet();
        } catch (err) {
            showToast(err.message, false);
        }
    };

    document.getElementById("maintenanceForm").onsubmit = async (e) => {
        e.preventDefault();
        const vid = document.getElementById("mVehicleId").value;
        try {
            await api(`/api/vehicles/${vid}/maintenance`, {
                method: "POST",
                body: JSON.stringify({
                    maintenanceType: document.getElementById("mType").value,
                    description: document.getElementById("mDesc").value,
                    scheduledDate: document.getElementById("mScheduledDate").value,
                    completedDate: document.getElementById("mCompletedDate").value || null,
                    markVehicleInMaintenance: document.getElementById("mMarkMaint").checked
                })
            });
            showToast(`Maintenance log saved for vehicle #${vid}.`);
            e.target.reset();
            document.getElementById("mScheduledDate").value = todayIso();
            loadSummaryAndFleet();
        } catch (err) {
            showToast(err.message, false);
        }
    };
}

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("bookStartDate").value = addDaysIso(1);
    document.getElementById("bookEndDate").value = addDaysIso(3);
    document.getElementById("mScheduledDate").value = todayIso();
    renderAuth();
    bindEvents();
    loadSummaryAndFleet();
});
