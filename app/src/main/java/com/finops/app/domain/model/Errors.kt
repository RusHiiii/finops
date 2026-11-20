package com.finops.app.domain.model

/** Saisie ou opération refusée par une règle métier ; [message] est affichable tel quel. */
class ValidationException(message: String) : Exception(message)

/** Donnée de marché indisponible (réseau, symbole inconnu, format de réponse changé). */
class MarketDataException(message: String, cause: Throwable? = null) : Exception(message, cause)
