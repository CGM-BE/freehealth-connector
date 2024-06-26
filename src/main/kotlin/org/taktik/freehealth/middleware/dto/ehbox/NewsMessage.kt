/*
 *
 * Copyright (C) 2018 Taktik SA
 *
 * This file is part of FreeHealthConnector.
 *
 * FreeHealthConnector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation.
 *
 * FreeHealthConnector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with FreeHealthConnector.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.taktik.freehealth.middleware.dto.ehbox

import org.taktik.freehealth.middleware.dto.common.Addressee
import org.taktik.freehealth.middleware.dto.common.Document

class NewsMessage(
    id: String? = null,
    publicationId: String? = null,
    contentType: String? = null,
    sender: Addressee? = null,
    mandatee: Addressee? = null,
    destinations: List<Addressee>? = null,
    important: Boolean = false,
    encrypted: Boolean = false,
    usePublicationReceipt: Boolean = false,
    useReceivedReceipt: Boolean = false,
    useReadReceipt: Boolean = false,
    hasAnnex: Boolean = false,
    hasFreeInformations: Boolean = false,
    publicationDateTime: Long? = null,
    expirationDateTime: Long? = null,
    size: String? = null,
    customMetas: Map<String, String>? = null,
    document: Document? = null,
    freeText: String? = null,
    freeInformationTableTitle: String? = null,
    freeInformationTableRows: Map<String, String> = HashMap(),
    patientInss: String? = null,
    annex: List<Document>? = ArrayList(),
    copyMailTo: List<String> = ArrayList(),
    documentTitle: String? = null,
    annexList: List<Document> = listOf()
) : DocumentMessage(
    id = id,
    publicationId = publicationId,
    contentType = contentType,
    sender = sender,
    mandatee = mandatee,
    destinations = destinations,
    important = important,
    encrypted = encrypted,
    usePublicationReceipt = usePublicationReceipt,
    useReceivedReceipt = useReceivedReceipt,
    useReadReceipt = useReadReceipt,
    hasAnnex = hasAnnex,
    hasFreeInformations = hasFreeInformations,
    publicationDateTime = publicationDateTime,
    expirationDateTime = expirationDateTime,
    size = size,
    customMetas = customMetas,
    document = document,
    freeText = freeText,
    freeInformationTableTitle = freeInformationTableTitle,
    freeInformationTableRows = freeInformationTableRows,
    patientInss = patientInss,
    annex = annex,
    copyMailTo = copyMailTo,
    documentTitle = documentTitle,
    annexList = annexList
)
