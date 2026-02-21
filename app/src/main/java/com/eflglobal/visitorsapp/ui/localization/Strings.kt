package com.eflglobal.visitorsapp.ui.localization

object Strings {

    // Welcome Screen
    fun welcome(lang: String) = if (lang == "es") "Bienvenido" else "Welcome"
    fun selectLanguage(lang: String) = if (lang == "es") "Seleccionar Idioma" else "Select Language"
    fun spanish(lang: String) = if (lang == "es") "Español" else "Spanish"
    fun english(lang: String) = if (lang == "es") "Inglés" else "English"
    fun newVisit(lang: String) = if (lang == "es") "Nueva Visita" else "New Visit"
    fun newVisitDesc(lang: String) = if (lang == "es") "Registrar un nuevo visitante" else "Register a new visitor"
    fun returningVisit(lang: String) = if (lang == "es") "Visita Recurrente" else "Returning Visit"
    fun returningVisitDesc(lang: String) = if (lang == "es") "Visitante ya registrado" else "Already registered visitor"
    fun endVisit(lang: String) = if (lang == "es") "Finalizar Visita" else "End Visit"
    fun endVisitDesc(lang: String) = if (lang == "es") "Escanear código QR de salida" else "Scan QR code to check out"

    // Document Scan Screen
    fun scanDocument(lang: String) = if (lang == "es") "Escanear Documento" else "Scan Document"
    fun iAmA(lang: String) = if (lang == "es") "Yo soy un:" else "I am a:"
    fun visitor(lang: String) = if (lang == "es") "Visitante" else "Visitor"
    fun contractor(lang: String) = if (lang == "es") "Contratista" else "Contractor"
    fun supplier(lang: String) = if (lang == "es") "Proveedor" else "Supplier"
    fun delivery(lang: String) = if (lang == "es") "Haciendo una entrega" else "Making a delivery"
    fun selectOption(lang: String) = if (lang == "es") "Seleccione una opción" else "Select an option"
    fun whatDocType(lang: String) = if (lang == "es") "¿Qué tipo de documento presenta?" else "What type of document do you present?"
    fun duiId(lang: String) = "DUI / ID"
    fun passport(lang: String) = if (lang == "es") "Pasaporte" else "Passport"
    fun other(lang: String) = if (lang == "es") "Otro" else "Other"
    fun frontOfDocument(lang: String) = if (lang == "es") "Frente del Documento" else "Front of Document"
    fun backOfDocument(lang: String) = if (lang == "es") "Reverso del Documento" else "Back of Document"
    fun scannedSuccessfully(lang: String) = if (lang == "es") "✓ Escaneado correctamente" else "✓ Scanned successfully"
    fun tapToScan(lang: String) = if (lang == "es") "Toque para escanear" else "Tap to scan"
    fun scanFrontFirst(lang: String) = if (lang == "es") "Escanee el frente primero" else "Scan front first"
    fun continueBtn(lang: String) = if (lang == "es") "Continuar" else "Continue"
    fun back(lang: String) = if (lang == "es") "Atrás" else "Back"

    // Document Camera Modal
    fun liveCameraView(lang: String) = if (lang == "es") "Vista de cámara en vivo" else "Live camera view"
    fun analyzingDocument(lang: String) = if (lang == "es") "Analizando documento..." else "Analyzing document..."
    fun checkingClarity(lang: String) = if (lang == "es") "Verificando claridad y legibilidad" else "Checking clarity and readability"
    fun placeDocumentFront(lang: String) = if (lang == "es")
        "Coloque el frente del documento dentro del marco.\nAsegúrese de que esté completamente visible y sin reflejos."
    else
        "Place the front of the document inside the frame.\nMake sure it is fully visible and glare-free."
    fun placeDocumentBack(lang: String) = if (lang == "es")
        "Coloque el reverso del documento dentro del marco.\nAsegúrese de que esté completamente visible y sin reflejos."
    else
        "Place the back of the document inside the frame.\nMake sure it is fully visible and glare-free."
    fun cancel(lang: String) = if (lang == "es") "Cancelar" else "Cancel"

    // Visitor Information Screen
    fun visitorInformation(lang: String) = if (lang == "es") "Información del Visitante" else "Visitor Information"
    fun personalInformation(lang: String) = if (lang == "es") "Información Personal" else "Personal Information"
    fun fullName(lang: String) = if (lang == "es") "Nombre y Apellido" else "Full Name"
    fun enterFullName(lang: String) = if (lang == "es") "Ingrese nombre completo" else "Enter full name"
    fun company(lang: String) = if (lang == "es") "Empresa" else "Company"
    fun optional(lang: String) = if (lang == "es") "Opcional" else "Optional"
    fun email(lang: String) = if (lang == "es") "Dirección de correo electrónico" else "Email Address"
    fun enterEmail(lang: String) = if (lang == "es") "ejemplo@correo.com" else "example@email.com"
    fun phone(lang: String) = if (lang == "es") "Número de teléfono" else "Phone Number"
    fun enterPhone(lang: String) = if (lang == "es") "Ingrese número de teléfono" else "Enter phone number"
    fun whoVisiting(lang: String) = if (lang == "es") "Nombre y Apellido de a quien visita" else "Full Name of Person Being Visited"
    fun enterWhoVisiting(lang: String) = if (lang == "es") "Ingrese nombre de la persona" else "Enter person's name"
    fun personalPhoto(lang: String) = if (lang == "es") "Fotografía Personal" else "Personal Photo"
    fun takePhoto(lang: String) = if (lang == "es") "Tomar Foto" else "Take Photo"
    fun retakePhoto(lang: String) = if (lang == "es") "Volver a Tomar" else "Retake Photo"
    fun photoTakenSuccessfully(lang: String) = if (lang == "es") "Foto tomada correctamente" else "Photo taken successfully"
    fun detectedFromDocument(lang: String) = if (lang == "es") "Detectado automáticamente del documento" else "Automatically detected from document"

    // Photo Capture Modal
    fun getReady(lang: String) = if (lang == "es") "¡Prepárese!" else "Get Ready!"
    fun lookAtCamera(lang: String) = if (lang == "es") "Mire a la cámara y sonría" else "Look at the camera and smile"
    fun photoWillBeTaken(lang: String) = if (lang == "es") "La foto se tomará automáticamente" else "Photo will be taken automatically"
    fun positionFace(lang: String) = if (lang == "es") "Posicione su rostro en el óvalo" else "Position your face in the oval"

    // Confirmation Screen
    fun confirmation(lang: String) = if (lang == "es") "Confirmación" else "Confirmation"
    fun verifyInformation(lang: String) = if (lang == "es") "Verificar Información" else "Verify Information"
    fun confirmSubtitle(lang: String) = if (lang == "es") "Por favor, confirme que los datos son correctos antes de finalizar el registro" else "Please confirm that the information is correct before completing the registration"
    fun documentsVerified(lang: String) = if (lang == "es") "Documentos Verificados" else "Documents Verified"
    fun frontAndBackScanned(lang: String) = if (lang == "es") "Frente y reverso escaneados correctamente" else "Front and back scanned successfully"
    fun confirmRegistration(lang: String) = if (lang == "es") "Confirmar Registro" else "Confirm Registration"
    fun editInformation(lang: String) = if (lang == "es") "Editar Información" else "Edit Information"
    fun visitorData(lang: String) = if (lang == "es") "Datos del Visitante" else "Visitor Data"
    fun visiting(lang: String) = if (lang == "es") "Visitando a" else "Visiting"
    fun visitTo(lang: String) = if (lang == "es") "Visita a" else "Visiting"
    fun type(lang: String) = if (lang == "es") "Tipo" else "Type"
    fun documentType(lang: String) = if (lang == "es") "Tipo de Documento" else "Document Type"
    fun documentNumber(lang: String) = if (lang == "es") "Número de Documento" else "Document Number"
    fun fullNameLabel(lang: String) = if (lang == "es") "Nombre Completo" else "Full Name"
    fun dateAndTime(lang: String) = if (lang == "es") "Fecha y Hora" else "Date and Time"
    fun registrationSuccess(lang: String) = if (lang == "es") "¡Registro Exitoso!" else "Registration Successful!"
    fun visitorRegisteredCorrectly(lang: String) = if (lang == "es") "El visitante ha sido registrado correctamente." else "The visitor has been registered successfully."
    fun edit(lang: String) = if (lang == "es") "Editar" else "Edit"
    fun confirm(lang: String) = if (lang == "es") "Confirmar" else "Confirm"

    // Success Screen
    fun success(lang: String) = if (lang == "es") "¡Éxito!" else "Success!"
    fun registrationComplete(lang: String) = if (lang == "es") "Registro Completado" else "Registration Complete"
    fun visitRegistered(lang: String) = if (lang == "es") "Su visita ha sido registrada exitosamente" else "Your visit has been registered successfully"
    fun printingQR(lang: String) = if (lang == "es") "Imprimiendo código QR..." else "Printing QR code..."
    fun noPrinterFound(lang: String) = if (lang == "es") "No se encontró impresora. Registro finalizado correctamente." else "No printer found. Registration completed successfully."
    fun keepQRCode(lang: String) = if (lang == "es") "Por favor, conserve este código QR para registrar su salida" else "Please keep this QR code to register your checkout"
    fun finish(lang: String) = if (lang == "es") "Finalizar" else "Finish"

    // Checkout QR Screen
    fun endVisitTitle(lang: String) = if (lang == "es") "Finalizar Visita" else "End Visit"
    fun scanQRCode(lang: String) = if (lang == "es") "Escanear Código QR" else "Scan QR Code"
    fun presentQR(lang: String) = if (lang == "es") "Presente el código QR de su visita para registrar la salida" else "Present your visit QR code to register checkout"
    fun placeQR(lang: String) = if (lang == "es") "Coloque el código QR\nen el área marcada" else "Place the QR code\nin the marked area"
    fun scanning(lang: String) = if (lang == "es") "Escaneando código QR..." else "Scanning QR code..."
    fun validCode(lang: String) = if (lang == "es") "¡Código Válido!" else "Valid Code!"
    fun processingCheckout(lang: String) = if (lang == "es") "Procesando salida..." else "Processing checkout..."
    fun startScanning(lang: String) = if (lang == "es") "Iniciar Escaneo" else "Start Scanning"
    fun useQRReceived(lang: String) = if (lang == "es") "ℹ️ Utilice el código QR que recibió al momento del registro de entrada" else "ℹ️ Use the QR code you received at check-in"
    fun checkoutRegistered(lang: String) = if (lang == "es") "¡Salida Registrada!" else "Checkout Registered!"
    fun checkoutSuccess(lang: String) = if (lang == "es") "¡Salida Registrada!" else "Checkout Successful!"
    fun checkoutTime(lang: String) = if (lang == "es") "Hora de salida" else "Checkout time"
    fun thanksForVisit(lang: String) = if (lang == "es") "Gracias por su visita" else "Thank you for your visit"
    fun accept(lang: String) = if (lang == "es") "Aceptar" else "Accept"

    // Recurring Visit Screen
    fun recurringVisit(lang: String) = if (lang == "es") "Visita Recurrente" else "Recurring Visit"
    fun searchRegistered(lang: String) = if (lang == "es") "Buscar Visitante Registrado" else "Search Registered Visitor"
    fun searchByName(lang: String) = if (lang == "es") "Buscar por nombre o documento..." else "Search by name or document..."
    fun noResults(lang: String) = if (lang == "es") "No se encontraron resultados" else "No results found"
    fun tryDifferentSearch(lang: String) = if (lang == "es") "Intente con un término de búsqueda diferente" else "Try a different search term"
    fun recentVisitors(lang: String) = if (lang == "es") "Visitantes Recientes" else "Recent Visitors"
    fun lastVisit(lang: String) = if (lang == "es") "Última visita" else "Last visit"
    fun selectVisitor(lang: String) = if (lang == "es") "Seleccionar" else "Select"

    // Station Setup Screen
    fun stationSetup(lang: String) = if (lang == "es") "Configuración de Estación" else "Station Setup"
    fun enterPIN(lang: String) = if (lang == "es") "Ingrese el PIN de 8 dígitos" else "Enter 8-digit PIN"
    fun pinPlaceholder(lang: String) = "••••••••"
    fun configureStation(lang: String) = if (lang == "es") "Configurar Estación" else "Configure Station"
    fun invalidPIN(lang: String) = if (lang == "es") "PIN inválido. Por favor, intente nuevamente." else "Invalid PIN. Please try again."
    fun setupInstructions(lang: String) = if (lang == "es")
        "Esta es la configuración inicial de la estación. Ingrese el PIN proporcionado por su administrador."
    else
        "This is the initial station setup. Enter the PIN provided by your administrator."
}

