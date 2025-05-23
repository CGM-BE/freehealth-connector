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

package org.taktik.freehealth.middleware.service.impl

import be.fgov.ehealth.commons.core.v1.StatusType
import be.fgov.ehealth.ehbox.consultation.protocol.v3.GetBoxInfoRequest
import be.fgov.ehealth.ehbox.consultation.protocol.v3.GetMessageAcknowledgmentsStatusRequest
import be.fgov.ehealth.ehbox.consultation.protocol.v3.GetMessagesListRequest
import be.fgov.ehealth.ehbox.consultation.protocol.v3.MessageRequestType
import be.fgov.ehealth.ehbox.consultation.protocol.v3.MoveMessageRequest
import be.fgov.ehealth.errors.core.v1.LocalisedStringType
import be.fgov.ehealth.errors.soa.v1.BusinessError
import be.fgov.ehealth.errors.soa.v1.EnvironmentType
import org.apache.commons.logging.LogFactory
import org.springframework.stereotype.Service
import org.taktik.connector.business.domain.ehbox.fault.FaultType
import org.taktik.connector.business.ehbox.v3.builders.impl.ConsultationMessageBuilderImpl
import org.taktik.connector.business.ehbox.v3.builders.impl.SendMessageBuilderImpl
import org.taktik.connector.business.ehbox.v3.exception.EhboxCryptoException
import org.taktik.connector.business.ehbox.v3.validator.impl.EhboxReplyValidatorImpl
import org.taktik.connector.technical.exception.RetryNextEndpointException
import org.taktik.connector.technical.exception.TechnicalConnectorException
import org.taktik.connector.technical.service.keydepot.KeyDepotService
import org.taktik.connector.technical.service.keydepot.impl.KeyDepotManagerImpl
import org.taktik.connector.technical.service.sts.security.impl.KeyStoreCredential
import org.taktik.freehealth.middleware.domain.common.Error
import org.taktik.freehealth.middleware.dto.common.ErrorDto
import org.taktik.freehealth.middleware.dto.ehbox.Acknowledgement
import org.taktik.freehealth.middleware.dto.ehbox.AltKeystore
import org.taktik.freehealth.middleware.dto.ehbox.BoxInfo
import org.taktik.freehealth.middleware.dto.ehbox.DocumentMessage
import org.taktik.freehealth.middleware.dto.ehbox.ErrorMessage
import org.taktik.freehealth.middleware.dto.ehbox.Message
import org.taktik.freehealth.middleware.dto.ehbox.MessageOperationResponse
import org.taktik.freehealth.middleware.dto.ehbox.MessageResponse
import org.taktik.freehealth.middleware.dto.ehbox.MessageStatusOperationResponse
import org.taktik.freehealth.middleware.dto.ehbox.MessagesResponse
import org.taktik.freehealth.middleware.exception.MissingTokenException
import org.taktik.freehealth.middleware.mapper.toDocumentMessage
import org.taktik.freehealth.middleware.mapper.toMessageDto
import org.taktik.freehealth.middleware.service.EhboxService
import org.taktik.freehealth.middleware.service.STSService
import org.taktik.freehealth.utils.mapFirstNotNull
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.util.UUID
import javax.xml.ws.soap.SOAPFaultException

@Service
class EhboxServiceImpl(private val stsService: STSService, keyDepotService: KeyDepotService) : EhboxService {
    private val freehealthEhboxService: org.taktik.connector.business.ehbox.service.EhboxService =
        org.taktik.connector.business.ehbox.service.impl.EhboxServiceImpl(EhboxReplyValidatorImpl())
    private val consultationMessageBuilder = ConsultationMessageBuilderImpl()
    private val sendMessageBuilder = SendMessageBuilderImpl(KeyDepotManagerImpl.getInstance(keyDepotService))
    private val log = LogFactory.getLog(this.javaClass)

    override fun getInfos(keystoreId: UUID, tokenId: UUID, passPhrase: String): BoxInfo {
        val samlToken = stsService.getSAMLToken(tokenId, keystoreId, passPhrase)
            ?: throw MissingTokenException("Cannot obtain token for Ehealth Box operations")
        val infoRequest = GetBoxInfoRequest()

        log.info("getInfos: ")
        return try {
            freehealthEhboxService.getBoxInfo(samlToken, infoRequest).let { response ->
                log.info("getInfos response: " + response.status?.code)
                return BoxInfo(
                    boxId = response.boxId.id,
                    quality = response.boxId.quality,
                    nbrMessagesInStandBy = response.nbrMessagesInStandBy,
                    currentSize = response.currentSize,
                    maxSize = response.maxSize
                              )
            }

        } catch (e: TechnicalConnectorException) {
            log.error(e)
            (e.cause as? SOAPFaultException)?.let {
                val be = parseFault(it.fault)?.details?.details?.firstOrNull()
                BoxInfo(error = ErrorDto(be?.code, be?.messages?.firstOrNull()?.value))
            } ?: BoxInfo(error = ErrorDto("999", e.message))
        }
    }

    override fun getFullMessage(keystoreId: UUID,
        tokenId: UUID,
        passPhrase: String,
        boxId: String,
        messageId: String,
        alternateKeystores: List<AltKeystore>?): MessageResponse {

        val samlToken = stsService.getSAMLToken(tokenId, keystoreId, passPhrase)
            ?: throw MissingTokenException("Cannot obtain token for Ehealth Box operations")
        val messageRequest = MessageRequestType().apply {
            this.messageId = messageId
            this.source = boxId
        }
        log.info("getFullMessage: ")
        return try {
            freehealthEhboxService.getFullMessage(samlToken, messageRequest).let { msg ->
                log.info("getFullMessage response: " + msg.status?.code)
                if (msg.status?.code == "100") try {
                    consultationMessageBuilder.buildFullMessage(
                        KeyStoreCredential(keystoreId, stsService.getKeyStore(keystoreId, passPhrase)!!, "authentication", passPhrase, samlToken.quality), msg).toMessageDto()?.let { MessageResponse(it) }
                        ?: MessageResponse(null, Error("Unknown error"))
                } catch (e: EhboxCryptoException) {
                    log.info("getFullMessage response: EhboxCryptoException")
                    alternateKeystores?.mapFirstNotNull {
                        try {
                            it.uuid?.let { uuid ->
                                it.passPhrase?.let { pass ->
                                    consultationMessageBuilder.buildFullMessage(KeyStoreCredential(uuid, stsService.getKeyStore(uuid, pass)!!, "authentication", pass, samlToken.quality), msg)
                                        .toMessageDto()
                                }
                            }
                        } catch (_: EhboxCryptoException) {
                            null
                        }
                    }?.let { MessageResponse(it) }
                        ?: MessageResponse(null, Error("Impossible to decrypt message using provided Keystores"))
                } else MessageResponse(null, Error(msg.status?.code, msg.status?.messages?.joinToString(",")))
            }
        } catch (e: TechnicalConnectorException) {
            log.error(e)
            (e.cause as? SOAPFaultException)?.let {
                val be = parseFault(it.fault)?.details?.details?.firstOrNull()
                MessageResponse(null, Error(be?.code, be?.messages?.firstOrNull()?.value))
            } ?: MessageResponse(null, Error("999", e.message))
        }
    }

    override fun sendMessage(
        keystoreId: UUID,
        tokenId: UUID,
        passPhrase: String,
        message: DocumentMessage,
        publicationReceipt: Boolean,
        receptionReceipt: Boolean,
        readReceipt: Boolean
                            ): MessageOperationResponse {

        val samlToken = stsService.getSAMLToken(tokenId, keystoreId, passPhrase)
            ?: throw MissingTokenException("Cannot obtain token for Ehealth Box operations")

        val request =
            sendMessageBuilder.buildMessage(
                keystoreId,
                stsService.getKeyStore(keystoreId, passPhrase)!!,
                samlToken.quality,
                passPhrase,
                message.toDocumentMessage()
                                           ).apply {
                contentContext.contentSpecification.let {
                    it.isPublicationReceipt =
                        publicationReceipt; it.isReceivedReceipt = receptionReceipt; it.isReadReceipt =
                    readReceipt
                }
            }
        request.publicationId = UUID.randomUUID().toString().substring(0, 12)
        log.info("sendMessage: ")
        return try {
            freehealthEhboxService.sendMessage(samlToken, request).let { sendMessageResponse ->
                log.info("sendMessage response: " + sendMessageResponse.status?.code)
                if (sendMessageResponse.status?.code == "100") MessageOperationResponse(success= true, messageId = sendMessageResponse.id) else MessageOperationResponse(false, Error(sendMessageResponse.status?.code, sendMessageResponse.status?.messages?.joinToString(",")))
            }
        } catch (e: RetryNextEndpointException) {
            MessageOperationResponse(false, Error("error.timeout",  e.message))
        } catch (e: TechnicalConnectorException) {
            log.error(e)
            (e.cause as? SOAPFaultException)?.let {
                val be = parseFault(it.fault)?.details?.details?.firstOrNull()
                MessageOperationResponse(false, Error(be?.code, be?.messages?.firstOrNull()?.value ?: it.message))
            } ?: MessageOperationResponse(false, Error("999", e.message))
        }
    }

    override fun sendMessage2Ebox(
        keystoreId: UUID,
        tokenId: UUID,
        passPhrase: String,
        message: DocumentMessage,
        publicationReceipt: Boolean,
        receptionReceipt: Boolean,
        readReceipt: Boolean
                            ): MessageOperationResponse {
        val samlToken = stsService.getSAMLToken(tokenId, keystoreId, passPhrase)
            ?: throw MissingTokenException("Cannot obtain token for Ehealth Box operations")

        val request =
            sendMessageBuilder.buildMessage(
                keystoreId,
                stsService.getKeyStore(keystoreId, passPhrase)!!,
                samlToken.quality,
                passPhrase,
                message.toDocumentMessage(),
                true
                                           ).apply {
                contentContext.contentSpecification.let {
                    it.isPublicationReceipt =
                        publicationReceipt; it.isReceivedReceipt = receptionReceipt; it.isReadReceipt =
                    readReceipt
                }
            }
        request.publicationId = UUID.randomUUID().toString().substring(0, 12)
        log.info("sendMessage2Ebox: ")
        return try {
            freehealthEhboxService.sendMessage2Ebox(samlToken, request).let { sendMessageResponse ->
                log.info("sendMessage2Ebox response: " + sendMessageResponse.status?.code)
                if (sendMessageResponse.status?.code == "100") MessageOperationResponse(true) else MessageOperationResponse(false, Error(sendMessageResponse.status?.code, sendMessageResponse.status?.messages?.joinToString(",")))
            }
        } catch (e: TechnicalConnectorException) {
            log.error(e)
            (e.cause as? SOAPFaultException)?.let {
                val be = parseFault(it.fault)?.details?.details?.firstOrNull()
                MessageOperationResponse(false, Error(be?.code, be?.messages?.firstOrNull()?.value))
            } ?: MessageOperationResponse(false, Error("999", e.message))
        }
    }

    override fun loadMessages(
        keystoreId: UUID,
        tokenId: UUID,
        passPhrase: String,
        boxId: String,
        limit: Int?,
        skip: Int,
        alternateKeystores: List<AltKeystore>?
                             ): MessagesResponse {
        val samlToken = stsService.getSAMLToken(tokenId, keystoreId, passPhrase)
            ?: throw MissingTokenException("Cannot obtain token for Ehealth Box operations")
        val messagesListRequest = GetMessagesListRequest()

        messagesListRequest.source = boxId
        messagesListRequest.startIndex = skip + 1
        messagesListRequest.endIndex = skip + 100

        val result = mutableListOf<Message>()
        var status: StatusType?

        log.info("loadMessages: ")

        return try {
            while (true) {
                val response = freehealthEhboxService.getMessageList(samlToken, messagesListRequest)
                log.info("loadMessages response: " + response.status?.code)
                status = response.status

                if (status?.code != "100") {
                    break
                }

                result.addAll(response.messages.mapNotNull { msg ->
                    try {
                        consultationMessageBuilder.buildMessage(
                            KeyStoreCredential(keystoreId, stsService.getKeyStore(keystoreId, passPhrase)!!, "authentication", passPhrase, samlToken.quality), msg
                                                               ).toMessageDto()
                    } catch (e: EhboxCryptoException) {
                        alternateKeystores?.mapFirstNotNull {
                            try {
                                it.uuid?.let { uuid ->
                                    it.passPhrase?.let { pass ->
                                        consultationMessageBuilder.buildMessage(KeyStoreCredential(uuid, stsService.getKeyStore(uuid, pass)!!, "authentication", pass, samlToken.quality), msg)
                                            .toMessageDto()
                                    }
                                }
                            } catch (_: EhboxCryptoException) {
                                null
                            }
                        } ?: ErrorMessage(title = "Impossible to decrypt message using provided Keystores")
                    }
                })
                if (response.messages.size < 100 || (limit != null && result.size >= limit)) {
                    break
                }
                messagesListRequest.startIndex = messagesListRequest.startIndex + 100
                messagesListRequest.endIndex = messagesListRequest.endIndex + 100
            }

            MessagesResponse(result, if (status?.code != "100") Error(status?.code, status?.messages?.joinToString(",")) else null)

        } catch (e: TechnicalConnectorException) {
            log.error(e)
            (e.cause as? SOAPFaultException)?.let {
                val be = parseFault(it.fault)?.details?.details?.firstOrNull()
                MessagesResponse(result, Error(be?.code, be?.messages?.firstOrNull()?.value))
            } ?: MessagesResponse(result, Error("999", e.message))
        }
    }

    override fun moveMessages(
        keystoreId: UUID,
        tokenId: UUID,
        passPhrase: String,
        messageIds: List<String>,
        source: String,
        destination: String
                             ): MessageOperationResponse {
        val samlToken = stsService.getSAMLToken(tokenId, keystoreId, passPhrase)
            ?: throw MissingTokenException("Cannot obtain token for Ehealth Box operations")
        val mmr = MoveMessageRequest()
        mmr.source = source
        mmr.destination = destination
        mmr.messageIds.addAll(messageIds)
        log.info("moveMessages: ")
        return try {
            freehealthEhboxService.moveMessage(samlToken, mmr)
                .let { moveMessageResult ->
                    log.info("moveMessages response: " + moveMessageResult.status?.code)
                    if (moveMessageResult.status?.code == "100") MessageOperationResponse(true) else MessageOperationResponse(false, Error(moveMessageResult.status?.code, moveMessageResult.status?.messages?.joinToString(",")))
                }
        } catch (e: TechnicalConnectorException) {
            log.error(e)
            (e.cause as? SOAPFaultException)?.let {
                val be = parseFault(it.fault)?.details?.details?.firstOrNull()
                MessageOperationResponse(false, Error(be?.code, be?.messages?.firstOrNull()?.value))
            } ?: MessageOperationResponse(false, Error("999", e.message))
        }
    }

    override fun getMessageAckStatus(
        keystoreId: UUID,
        tokenId: UUID,
        passPhrase: String,
        messageId: String
    ): MessageStatusOperationResponse {
        val samlToken = stsService.getSAMLToken(tokenId, keystoreId, passPhrase)
            ?: throw MissingTokenException("Cannot obtain token for Ehealth Box operations")
        val asr = GetMessageAcknowledgmentsStatusRequest().apply {
            this.messageId = messageId
            this.startIndex = 1
            this.endIndex = 100
        }
        log.info("getMessageAckStatus: ")
        return try {
            freehealthEhboxService.getMessageAcknowledgmentsStatusResponse(samlToken, asr)
                .let { statusMessageResult ->
                    log.info("getMessageAckStatus response: " + statusMessageResult.status?.code)
                    if (statusMessageResult.status?.code == "100") MessageStatusOperationResponse(true, acks = statusMessageResult.acknowledgmentsStatus.rows.map { Acknowledgement(it.recipient, it.published?.millis, it.received?.millis, it.read?.millis) }) else MessageStatusOperationResponse(false, Error(statusMessageResult.status?.code, statusMessageResult.status?.messages?.joinToString(",")))
                }
        } catch (e: TechnicalConnectorException) {
            log.error(e)
            (e.cause as? SOAPFaultException)?.let {
                val be = parseFault(it.fault)?.details?.details?.firstOrNull()
                MessageStatusOperationResponse(false, Error(be?.code, be?.messages?.firstOrNull()?.value))
            } ?: MessageStatusOperationResponse(false, Error("999", e.message))
        }
    }


    override fun deleteMessages(
        keystoreId: UUID,
        tokenId: UUID,
        passPhrase: String,
        messageIds: List<String>,
        source: String
                               ): MessageOperationResponse {
        val samlToken = stsService.getSAMLToken(tokenId, keystoreId, passPhrase)
            ?: throw MissingTokenException("Cannot obtain token for Ehealth Box operations")
        val mmr = be.fgov.ehealth.ehbox.consultation.protocol.v3.DeleteMessageRequest()
        mmr.source = source
        mmr.messageIds.addAll(messageIds)
        log.info("deleteMessages: ")
        return try {
            freehealthEhboxService.deleteMessage(samlToken, mmr).let { deleteMessageResult ->
                log.info("deleteMessages response: " + deleteMessageResult.status?.code)
                if (deleteMessageResult.status?.code == "100") MessageOperationResponse(true) else MessageOperationResponse(false, Error(deleteMessageResult.status?.code, deleteMessageResult.status?.messages?.joinToString(",")))
            }
        } catch (e: TechnicalConnectorException) {
            log.error(e)
            (e.cause as? SOAPFaultException)?.let {
                val be = parseFault(it.fault)?.details?.details?.firstOrNull()
                MessageOperationResponse(false, Error(be?.code, be?.messages?.firstOrNull()?.value))
            } ?: MessageOperationResponse(false, Error("999", e.message))
        }
    }

    private fun parseFault(fault: Element?): FaultType? {
        return fault?.let {
            org.taktik.connector.business.domain.ehbox.fault.FaultType().apply {
                faultCode =
                    it.getElementsByTagNameWithOrWithoutNs("http://schemas.xmlsoap.org/soap/envelope/", "faultcode")
                        .item(0)?.textContent
                faultString =
                    it.getElementsByTagNameWithOrWithoutNs("http://schemas.xmlsoap.org/soap/envelope/", "faultstring")
                        .item(0)?.textContent
                details = org.taktik.connector.business.domain.ehbox.fault.DetailsType().apply {
                    (it.getElementsByTagNameWithOrWithoutNs("urn:be:fgov:ehealth:errors:soa:v1", "BusinessError").item(0) as? Element)?.let {
                        details.add(BusinessError().apply {
                            origin =
                                it.getElementsByTagNameWithOrWithoutNs("urn:be:fgov:ehealth:errors:soa:v1", "Origin")
                                    .item(0)?.textContent
                            code =
                                it.getElementsByTagNameWithOrWithoutNs("urn:be:fgov:ehealth:errors:soa:v1", "Code")
                                    .item(0)
                                    ?.textContent
                            id = it.attributes.getNamedItem("Id")?.textContent
                            environment =
                                EnvironmentType.fromValue(it.getElementsByTagNameWithOrWithoutNs("urn:be:fgov:ehealth:errors:soa:v1", "Environment").item(0)?.textContent)
                            messages.add(LocalisedStringType().apply {
                                it.getElementsByTagNameWithOrWithoutNs("urn:be:fgov:ehealth:errors:soa:v1", "Message")
                                    .item(0)?.let {
                                        lang =
                                            it.attributes.getNamedItemWithOrWithoutNs("http://www.w3.org/XML/1998/namespace", "lang")
                                                ?.textContent
                                        value = it.textContent
                                    }
                            })
                        })
                    }
                }
            }
        }
    }

    private fun Element.getElementsByTagNameWithOrWithoutNs(ns: String, name: String): NodeList {
        return this.getElementsByTagNameNS(ns, name).let { if (it.length > 0) it else this.getElementsByTagName(name) }
    }

    private fun NamedNodeMap.getNamedItemWithOrWithoutNs(ns: String, name: String): Node? {
        return this.getNamedItemNS(ns, name) ?: this.getNamedItem(name)
    }

}
