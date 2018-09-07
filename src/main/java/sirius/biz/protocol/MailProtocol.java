/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.elastic.protocol;

import sirius.biz.elastic.SearchContent;
import sirius.biz.elastic.SearchableEntity;
import sirius.db.es.annotations.ESOption;
import sirius.db.es.annotations.IndexMode;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.di.std.Framework;

import java.time.LocalDateTime;

/**
 * Stores a copy of a mail sent by the system.
 */
@Framework(Protocols.FRAMEWORK_PROTOCOLS)
public class MailProtocol extends SearchableEntity {

    /**
     * Contains the timestamp when the mail was sent.
     */
    public static final Mapping TOD = Mapping.named("tod");
    private LocalDateTime tod = LocalDateTime.now();

    /**
     * Determines if sending the mail was successful.
     */
    public static final Mapping SUCCESS = Mapping.named("success");
    private boolean success = true;

    /**
     * Contains the id assigned by the mailing system.
     */
    public static final Mapping MESSAGE_ID = Mapping.named("messageId");
    @NullAllowed
    @SearchContent
    private String messageId;

    /**
     * Contains the address of the sender.
     */
    public static final Mapping SENDER = Mapping.named("sender");
    @SearchContent
    private String sender;

    /**
     * Contains the name of the sender.
     */
    public static final Mapping SENDER_NAME = Mapping.named("senderName");
    @NullAllowed
    @SearchContent
    private String senderName;

    /**
     * Contains the address of the receiver.
     */
    public static final Mapping RECEIVER = Mapping.named("receiver");
    @SearchContent
    private String receiver;

    /**
     * Contains the name of the receiver.
     */
    public static final Mapping RECEIVER_NAME = Mapping.named("receiverName");
    @NullAllowed
    @SearchContent
    private String receiverName;

    /**
     * Contains the subject.
     */
    public static final Mapping SUBJECT = Mapping.named("subject");
    @SearchContent
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String subject;

    /**
     * Contains the text contents.
     */
    public static final Mapping TEXT_CONTENT = Mapping.named("textContent");
    @NullAllowed
    @SearchContent
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String textContent;

    /**
     * Contains the HTML contents.
     */
    public static final Mapping HTML_CONTENT = Mapping.named("htmlContent");
    @NullAllowed
    @SearchContent
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String htmlContent;

    /**
     * Contains the type of the mail (if given).
     */
    public static final Mapping TYPE = Mapping.named("type");
    @NullAllowed
    private String type;

    /**
     * Contains the node which sent the email.
     */
    public static final Mapping NODE = Mapping.named("node");
    @SearchContent
    private String node;

    public LocalDateTime getTod() {
        return tod;
    }

    public void setTod(LocalDateTime tod) {
        this.tod = tod;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public String getHtmlContent() {
        return htmlContent;
    }

    public void setHtmlContent(String htmlContent) {
        this.htmlContent = htmlContent;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
