# EFL Visitor App

<p align="center">
  <img src="app/src/main/res/drawable/logoefl.png" alt="EFL Logo" width="160"/>
</p>

<p align="center">
  <strong>Sistema de Control de Visitas — Visitor Management System</strong><br/>
  <em>EFL Global · Android Tablet Application</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?logo=android" />
  <img src="https://img.shields.io/badge/Min%20SDK-Android%208.0%20(API%2026)-blue" />
  <img src="https://img.shields.io/badge/Language-Kotlin-purple?logo=kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-orange" />
  <img src="https://img.shields.io/badge/Version-1.0.0-lightgrey" />
  <img src="https://img.shields.io/badge/Status-In%20Development-yellow" />
</p>

---

## 🇪🇸 Español

### ¿Qué es EFL Visitor App?

**EFL Visitor App** es una aplicación Android diseñada para tablets que permite gestionar de forma digital el registro de visitantes en instalaciones corporativas. Reemplaza los registros manuales en papel con un flujo completo, seguro y auditable desde cualquier punto de acceso o recepción.

### Funcionalidades Principales

- 📋 **Registro de nuevas visitas** — Captura de datos personales, escaneo de documento de identidad y fotografía del visitante
- 🔄 **Visitas recurrentes** — Reconocimiento de visitantes previos con carga automática de su historial más reciente
- 📷 **Escaneo inteligente de documentos** — Captura automática con validación de nitidez, recorte del documento, extracción de datos por OCR y verificación de autenticidad
- 🪪 **Generación de carnet de visita** — Badge imprimible con foto, QR, datos de la visita y logo corporativo
- 🖨️ **Impresión automática** — Soporte para impresoras térmicas **Brother** y **Zebra** por red Wi-Fi o USB
- 🏢 **Panel de administración** — Historial de visitas, filtros avanzados, detalle de cada registro y gestión de estaciones
- 🌐 **Multiidioma** — Soporte completo para **Español**, **Inglés**, **Francés** y **Portugués**
- 🔒 **Datos locales seguros** — Toda la información se almacena en la tablet de forma segura y estructurada

### Flujo de Registro

```
Inicio de App
    ↓
Selección de idioma
    ↓
¿Visita nueva o recurrente?
    ↓
Escaneo de documento frontal y reverso
    ↓
Toma de fotografía con detección de rostro
    ↓
Ingreso de información: nombre, empresa, a quién visita, motivo
    ↓
Confirmación y generación de carnet
    ↓
Impresión automática del badge de visita
```

### Tipos de Visitante

| Tipo | Descripción |
|---|---|
| Visitante | Visita personal o general |
| Conductor | Transporte o logística |
| Contratista | Servicios externos |
| Personal Temporal | Trabajo en instalaciones por tiempo limitado |
| Entrega / Recolecta | Servicios de mensajería o logística |
| Proveedor | Servicios comerciales |
| Entrevista | Proceso de selección |
| Servicio Técnico | Soporte o mantenimiento |
| Trabajo en Sitio | Actividades operativas en la empresa |
| Otro | Campo personalizado libre |

### Idiomas Soportados

| Idioma | Código |
|---|---|
| 🇬🇧 Inglés | `en` |
| 🇪🇸 Español | `es` |
| 🇫🇷 Francés | `fr` |
| 🇧🇷 Portugués | `pt` |

### Estado del Proyecto

> ⚠️ **Versión 1.0.0 — En desarrollo activo**
>
> La aplicación se encuentra en fase de desarrollo funcional. La interfaz completa está implementada y todas las funcionalidades core están operativas para pruebas en dispositivos físicos.

---

## 🇺🇸 English

### What is EFL Visitor App?

**EFL Visitor App** is an Android tablet application designed to digitally manage visitor registration at corporate facilities. It replaces traditional paper-based logbooks with a complete, secure, and auditable flow from any access point or reception area.

### Key Features

- 📋 **New visitor registration** — Personal data capture, identity document scanning and visitor photograph
- 🔄 **Returning visitors** — Recognition of previous visitors with automatic load of their most recent record
- 📷 **Smart document scanning** — Automatic capture with sharpness validation, document cropping, OCR data extraction and authenticity verification
- 🪪 **Visitor badge generation** — Printable badge with photo, QR code, visit details and corporate logo
- 🖨️ **Automatic printing** — Support for **Brother** and **Zebra** thermal printers via Wi-Fi or USB
- 🏢 **Admin panel** — Visit history, advanced filters, full record details and station management
- 🌐 **Multilanguage** — Full support for **Spanish**, **English**, **French** and **Portuguese**
- 🔒 **Secure local data** — All information is stored securely and structured on the tablet

### Registration Flow

```
App Start
    ↓
Language selection
    ↓
New visit or returning visitor?
    ↓
Front and back document scan
    ↓
Photo capture with face detection
    ↓
Enter information: name, company, who to visit, reason
    ↓
Confirmation and badge generation
    ↓
Automatic badge printing
```

### Visitor Types

| Type | Description |
|---|---|
| Visitor | Personal or general visit |
| Driver | Transportation or logistics |
| Contractor | External services |
| Temporary Staff | On-site work for a limited time |
| Delivery / Collection | Courier or logistics services |
| Vendor | Commercial services |
| Interview | Recruitment process |
| Technical Service | Support or maintenance |
| On-Site Work | Operational activities at the facility |
| Other | Custom free-text field |

### Supported Languages

| Language | Code |
|---|---|
| 🇬🇧 English | `en` |
| 🇪🇸 Spanish | `es` |
| 🇫🇷 French | `fr` |
| 🇧🇷 Portuguese | `pt` |

### Project Status

> ⚠️ **Version 1.0.0 — Active Development**
>
> The application is in active functional development. The complete UI is implemented and all core functionalities are operational for testing on physical devices.

---

## 📱 Technical Requirements / Requisitos Técnicos

| | |
|---|---|
| **Platform / Plataforma** | Android |
| **Minimum Android / Android Mínimo** | Android 8.0 Oreo (API 26) |
| **Target Android / Android Objetivo** | Android 14 (API 36) |
| **Device / Dispositivo** | Tablet (recommended 10"+ / recomendado 10"+) |
| **Camera / Cámara** | Rear camera required / Cámara trasera requerida |
| **Printers / Impresoras** | Brother QL Series, Zebra ZT Series |
| **Connectivity / Conectividad** | Wi-Fi (for printing / para impresión) |

---

## 🖨️ Supported Printers / Impresoras Compatibles

| Brand / Marca | Models tested / Modelos probados | Connection / Conexión |
|---|---|---|
| **Brother** | QL-820NWB | Wi-Fi (TCP/IP) |
| **Zebra** | ZT230 | USB / Wi-Fi |

---

## 🔮 Roadmap — Cloud Sync (Next Phase / Próxima Fase)

The next development phase will focus on connecting the app to a cloud backend to enable:

La próxima fase de desarrollo se enfocará en conectar la app a un backend en la nube para habilitar:

- ☁️ Real-time data synchronization across multiple stations / Sincronización en tiempo real entre múltiples estaciones
- 🖥️ Web-based administration and reporting panel / Panel web de administración y consultas
- 📊 Advanced analytics by station, date, visitor type / Analítica avanzada por estación, fecha, tipo de visita
- 🔐 Centralized authentication / Autenticación centralizada
- 🗄️ Persistent cloud storage for images and records / Almacenamiento persistente en la nube para imágenes y registros

Planned infrastructure: **Microsoft Azure (VM or PaaS)** — see [`docs/BACKEND_SYNC_PLAN.md`](docs/BACKEND_SYNC_PLAN.md) for details.

Infraestructura planificada: **Microsoft Azure (VM o PaaS)** — ver [`docs/BACKEND_SYNC_PLAN.md`](docs/BACKEND_SYNC_PLAN.md) para detalles.

---

## ⚖️ License & Copyright / Licencia y Derechos

<p align="center">
  <img src="https://img.shields.io/badge/License-Proprietary-red" />
</p>

This project is **proprietary software** owned by **EFL Global**.  
All rights reserved. See the [`LICENSE`](LICENSE) file for full terms.

Este proyecto es **software propietario** de **EFL Global**.  
Todos los derechos reservados. Consulta el archivo [`LICENSE`](LICENSE) para los términos completos.

---

<p align="center">
  <strong>EFL Global · EFL Visitor App · v1.0.0</strong><br/>
  <em>© 2026 EFL Global. All rights reserved / Todos los derechos reservados.</em>
</p>

