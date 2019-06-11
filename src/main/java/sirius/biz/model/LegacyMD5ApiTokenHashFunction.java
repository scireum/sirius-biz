/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import sirius.kernel.di.std.Register;

/**
 * Uses md5 hashing to hash the apiToken, but includes the time as long and not as string in the hash.
 */
@Register
public class LegacyMD5ApiTokenHashFunction implements ApiTokenHashFunction {

    @Override
    public String computeHash(String apiToken, long time) {
        return Hashing.md5().newHasher().putString(apiToken, Charsets.UTF_8).putLong(time).hash().toString();
    }

    @Override
    public int getPriority() {
        return 1000;
    }
}
