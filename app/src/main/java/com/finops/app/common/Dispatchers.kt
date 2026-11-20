package com.finops.app.common

import javax.inject.Qualifier

/** Dispatcher pour les entrées/sorties (réseau, disque). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatcher pour les calculs CPU (reconstitution d'historique…). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
