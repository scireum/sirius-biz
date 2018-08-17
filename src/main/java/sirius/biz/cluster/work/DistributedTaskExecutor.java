/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import com.alibaba.fastjson.JSONObject;

public abstract class DistributedTaskExecutor {

    public abstract String queueName();

    public abstract void executeWork(JSONObject context) throws Exception;
}
