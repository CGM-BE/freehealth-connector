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

package org.taktik.freehealth.middleware.web.controllers

import ma.glasnost.orika.MapperFacade
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.taktik.freehealth.middleware.dto.efact.InvoiceFlatFile
import org.taktik.freehealth.middleware.dto.efact.InvoicesBatch
import org.taktik.freehealth.middleware.service.EfactService
import java.util.*

@RestController
@RequestMapping("/efact")
class EfactController(val efactService: EfactService, val mapper: MapperFacade) {
    @PostMapping("/batch", produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
    fun sendBatch(
        @RequestHeader(name = "X-FHC-keystoreId") keystoreId: UUID,
        @RequestHeader(name = "X-FHC-tokenId") tokenId: UUID,
        @RequestHeader(name = "X-FHC-passPhrase") passPhrase: String,
        @RequestBody batch: InvoicesBatch
                 ) =
        efactService.sendBatch(
            keystoreId = keystoreId,
            tokenId = tokenId,
            passPhrase = passPhrase,
            batch = batch
        )

    @PostMapping("/flatfile", produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
    fun sendFlatFile(
        @RequestHeader(name = "X-FHC-keystoreId") keystoreId: UUID,
        @RequestHeader(name = "X-FHC-tokenId") tokenId: UUID,
        @RequestHeader(name = "X-FHC-passPhrase") passPhrase: String,
        @RequestBody invoice: InvoiceFlatFile
    ) =
        efactService.sendFlatFile(
            keystoreId = keystoreId,
            tokenId = tokenId,
            passPhrase = passPhrase,
            invoice = invoice
        )

    @PostMapping("/flat", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun makeFlatFile(
        @RequestBody batch: InvoicesBatch
                    ) =
        efactService.makeFlatFile(
            batch = batch,
            isTest = false
                                 )

    @PostMapping("/flatcore", produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
    fun makeFlatFileCore(
        @RequestBody batch: InvoicesBatch
                    ) =
        efactService.makeFlatFileCoreWithMetadata(
            batch = batch,
            isTest = false
                                 )

    @PostMapping("/flat/test", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun makeFlatFileTest(
        @RequestBody batch: InvoicesBatch
                        ) =
        efactService.makeFlatFile(
            batch = batch,
            isTest = true
                                 )

    @GetMapping("/{nihii}/{language}", produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
    fun loadMessages(
        @PathVariable nihii: String,
        @PathVariable language: String,
        @RequestHeader(name = "X-FHC-keystoreId") keystoreId: UUID,
        @RequestHeader(name = "X-FHC-tokenId") tokenId: UUID,
        @RequestHeader(name = "X-FHC-passPhrase") passPhrase: String,
        @RequestParam ssin: String,
        @RequestParam firstName: String,
        @RequestParam lastName: String,
        @RequestParam limit: Int?
                    ) =
        efactService.loadMessages(
            keystoreId = keystoreId,
            tokenId = tokenId,
            passPhrase = passPhrase,
            hcpSsin = ssin,
            hcpNihii = nihii,
            hcpFirstName = firstName,
            hcpLastName = lastName,
            language = language,
            limit = limit ?: Integer.MAX_VALUE
                                 )

    @PutMapping("/confirm/acks/{nihii}", produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
    fun confirmAcks(
        @PathVariable nihii: String,
        @RequestHeader(name = "X-FHC-keystoreId") keystoreId: UUID,
        @RequestHeader(name = "X-FHC-tokenId") tokenId: UUID,
        @RequestHeader(name = "X-FHC-passPhrase") passPhrase: String,
        @RequestParam ssin: String,
        @RequestParam firstName: String,
        @RequestParam lastName: String,
        @RequestBody valueHashes: List<String>
               ) =
        efactService.confirmAcks(
            keystoreId = keystoreId,
            tokenId = tokenId,
            passPhrase = passPhrase,
            hcpNihii = nihii,
            hcpSsin = ssin,
            hcpFirstName = firstName,
            hcpLastName = lastName,
            valueHashes = valueHashes
        )

    @PutMapping("/confirm/msgs/{nihii}", produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
    fun confirmMessages(
        @PathVariable nihii: String,
        @RequestHeader(name = "X-FHC-keystoreId") keystoreId: UUID,
        @RequestHeader(name = "X-FHC-tokenId") tokenId: UUID,
        @RequestHeader(name = "X-FHC-passPhrase") passPhrase: String,
        @RequestParam ssin: String,
        @RequestParam firstName: String,
        @RequestParam lastName: String,
        @RequestBody valueHashes: List<String>
    ) =
        efactService.confirmMessages(
            keystoreId = keystoreId,
            tokenId = tokenId,
            passPhrase = passPhrase,
            hcpNihii = nihii,
            hcpSsin = ssin,
            hcpFirstName = firstName,
            hcpLastName = lastName,
            valueHashes = valueHashes
        )
}
