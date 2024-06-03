/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants

import sirius.biz.model.ContactData
import sirius.db.mongo.MongoEntity

class ContactDataTestEntity: MongoEntity() {

    public val contact: ContactData = ContactData()
}
