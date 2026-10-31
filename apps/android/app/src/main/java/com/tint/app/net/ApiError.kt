package com.tint.app.net

/** Error de identity-api: `status` HTTP (0 si no hubo respuesta) y el `error` del cuerpo. */
class ApiError(val status: Int, message: String) : Exception(message)
