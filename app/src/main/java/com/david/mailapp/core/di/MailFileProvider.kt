package com.david.mailapp.core.di

import androidx.core.content.FileProvider

/**
 * Subclase vacía de [FileProvider] para exponer PDFs cacheados al visor
 * externo de Android mediante content:// URIs con permisos temporales.
 *
 * Registrada en AndroidManifest.xml con authority ${applicationId}.fileprovider.
 * La ruta expuesta se define en res/xml/pdf_file_paths.xml.
 */
class MailFileProvider : FileProvider()
