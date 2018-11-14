/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import com.alibaba.fastjson.JSONObject;
import sirius.kernel.di.std.Named;

public interface TableCellFormat extends Named {

    String format(JSONObject data);

}
