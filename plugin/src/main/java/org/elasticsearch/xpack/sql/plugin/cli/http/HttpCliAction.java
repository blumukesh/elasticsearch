/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.plugin.cli.http;

import java.io.DataInputStream;
import java.io.IOException;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.xpack.sql.cli.net.protocol.ProtoUtils;
import org.elasticsearch.xpack.sql.net.client.util.StringUtils;
import org.elasticsearch.xpack.sql.plugin.cli.action.CliAction;
import org.elasticsearch.xpack.sql.plugin.cli.action.CliRequest;
import org.elasticsearch.xpack.sql.plugin.cli.action.CliResponse;

import static org.elasticsearch.action.ActionListener.wrap;
import static org.elasticsearch.rest.BytesRestResponse.TEXT_CONTENT_TYPE;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestStatus.BAD_REQUEST;
import static org.elasticsearch.rest.RestStatus.INTERNAL_SERVER_ERROR;
import static org.elasticsearch.rest.RestStatus.OK;

public class HttpCliAction extends BaseRestHandler {

    public HttpCliAction(Settings settings, RestController controller) {
        super(settings);
        controller.registerHandler(POST, "/_cli", this);
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!request.hasContent()) {
            return badProto(StringUtils.EMPTY);
        }

        try (DataInputStream in = new DataInputStream(request.content().streamInput())) {
            String msg = ProtoUtils.readHeader(in);
            if (msg != null) {
                return badProto(msg);
            }

            try {
                return c -> client.executeLocally(CliAction.INSTANCE, new CliRequest(ProtoUtils.readRequest(in)),
                                                    wrap(response -> cliResponse(c, response), ex -> error(c, ex)));

            } catch (Exception ex) {
                return badProto("Unknown message");
            }
        }
    }
    
    private static RestChannelConsumer badProto(String message) {
        return c -> c.sendResponse(new BytesRestResponse(BAD_REQUEST, TEXT_CONTENT_TYPE, message));
    }

    private static void cliResponse(RestChannel channel, CliResponse response) {
        BytesRestResponse restResponse = null;
        
        try {
            restResponse = new BytesRestResponse(OK, TEXT_CONTENT_TYPE, CliServerProtoUtils.write(response.response()));
        } catch (IOException ex) {
            restResponse = new BytesRestResponse(INTERNAL_SERVER_ERROR, TEXT_CONTENT_TYPE, StringUtils.EMPTY);
        }

        channel.sendResponse(restResponse);
    }

    private void error(RestChannel channel, Exception ex) {
        logger.debug("failed to parse sql request", ex);
        BytesRestResponse response = null;
        try {
            response = new BytesRestResponse(channel, ex);
        } catch (IOException e) {
            response = new BytesRestResponse(OK, TEXT_CONTENT_TYPE, ExceptionsHelper.stackTrace(e));
        }
        channel.sendResponse(response);
    }

    @Override
    public String getName() {
        return "sql_cli_action";
    }
}