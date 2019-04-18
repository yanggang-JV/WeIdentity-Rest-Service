/*
 *       Copyright© (2019) WeBank Co., Ltd.
 *
 *       This file is part of weidentity-java-sdk.
 *
 *       weidentity-java-sdk is free software: you can redistribute it and/or modify
 *       it under the terms of the GNU Lesser General Public License as published by
 *       the Free Software Foundation, either version 3 of the License, or
 *       (at your option) any later version.
 *
 *       weidentity-java-sdk is distributed in the hope that it will be useful,
 *       but WITHOUT ANY WARRANTY; without even the implied warranty of
 *       MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *       GNU Lesser General Public License for more details.
 *
 *       You should have received a copy of the GNU Lesser General Public License
 *       along with weidentity-java-sdk.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.webank.weid.http.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.webank.weid.constant.ErrorCode;
import com.webank.weid.constant.ParamKeyConstant;
import com.webank.weid.http.constant.HttpReturnCode;
import com.webank.weid.http.constant.WeIdentityParamKeyConstant;
import com.webank.weid.http.protocol.request.InputArg;
import com.webank.weid.http.protocol.response.HttpResponseData;
import com.webank.weid.http.service.BaseService;
import com.webank.weid.http.service.InvokerCptService;
import com.webank.weid.http.util.InputUtil;
import com.webank.weid.http.util.PrivateKeyUtil;
import com.webank.weid.protocol.base.WeIdAuthentication;
import com.webank.weid.protocol.base.WeIdPrivateKey;
import com.webank.weid.protocol.request.CptStringArgs;
import com.webank.weid.protocol.response.ResponseData;
import com.webank.weid.rpc.CptService;
import com.webank.weid.rpc.RawTransactionService;
import com.webank.weid.service.impl.CptServiceImpl;
import com.webank.weid.service.impl.RawTransactionServiceImpl;
import com.webank.weid.util.JsonUtil;

@Component
public class InvokerCptServiceImpl extends BaseService implements InvokerCptService {

    private Logger logger = LoggerFactory.getLogger(InvokerCptService.class);

    private CptService cptService = new CptServiceImpl();

    private RawTransactionService rawTransactionService = new RawTransactionServiceImpl();

    /**
     * Register a new CPT to blockchain via Invoke function.
     *
     * @param registerArgs the args
     * @return the resp data
     */
    public HttpResponseData<Object> registerCptInvoke(InputArg registerArgs) {
        try {
            JsonNode functionArgNode = new ObjectMapper()
                .readTree(registerArgs.getFunctionArg());
            JsonNode weIdNode = functionArgNode.get(ParamKeyConstant.WEID);
            JsonNode cptJsonSchemaNode = functionArgNode.get(ParamKeyConstant.CPT_JSON_SCHEMA);
            JsonNode txnArgNode = new ObjectMapper()
                .readTree(registerArgs.getTransactionArg());
            JsonNode keyIndexNode = txnArgNode.get(WeIdentityParamKeyConstant.KEY_INDEX);
            if (weIdNode == null || StringUtils.isEmpty(weIdNode.textValue())
                || cptJsonSchemaNode == null || StringUtils.isEmpty(cptJsonSchemaNode.toString())
                || keyIndexNode == null || StringUtils.isEmpty(keyIndexNode.textValue())) {
                return new HttpResponseData<>(null, HttpReturnCode.INPUT_NULL);
            }

            String weIdPrivKey = PrivateKeyUtil
                .getPrivateKeyByWeId(PrivateKeyUtil.SDK_PRIVKEY_PATH, keyIndexNode.textValue());
            if (StringUtils.isEmpty(weIdPrivKey)) {
                return new HttpResponseData<>(null, HttpReturnCode.INVOKER_ILLEGAL);
            }
            WeIdAuthentication weIdAuthentication = this.buildWeIdAuthority(
                weIdPrivKey,
                weIdNode.textValue());
            CptStringArgs cptStringArgs = new CptStringArgs();
            cptStringArgs.setWeIdAuthentication(weIdAuthentication);
            cptStringArgs.setCptJsonSchema(cptJsonSchemaNode.toString());
            ResponseData response;
            try {
                response = cptService.registerCpt(cptStringArgs);
            } catch (Exception e) {
                logger.error(
                    "[queryCpt]: unknow error. cptId:{}.",
                    registerArgs,
                    e);
                return new HttpResponseData<>(null, HttpReturnCode.WEID_SDK_ERROR);
            }
            return new HttpResponseData<>(
                InputUtil.convertJsonToSortedMap(JsonUtil.objToJsonStr(response.getResult())),
                response.getErrorCode(),
                response.getErrorMessage());
        } catch (Exception e) {
            logger.error(
                "[queryCpt]: unknow error. cptId:{}.",
                registerArgs,
                e);
            return new HttpResponseData<>(null, HttpReturnCode.UNKNOWN_ERROR.getCode(),
                HttpReturnCode.UNKNOWN_ERROR.getCodeDesc().concat(e.getMessage()));
        }
    }

    /**
     * Call to WeID SDK with direct transaction hex String, to register CPT.
     *
     * @param transactionHex the transactionHex value
     * @return String in ResponseData
     */
    public HttpResponseData<String> registerCptWithTransactionHex(String transactionHex) {
        try {
            ResponseData<String> responseData = rawTransactionService.registerCpt(transactionHex);
            if (responseData.getErrorCode() != ErrorCode.SUCCESS.getCode()) {
                logger.error("[registerCpt]: error occurred: {}, {}", responseData.getErrorCode(),
                    responseData.getErrorMessage());
            }
            return new HttpResponseData<>(responseData.getResult(), responseData.getErrorCode(),
                responseData.getErrorMessage());
        } catch (Exception e) {
            logger.error("[registerCpt]: unknown error, input arguments:{}",
                transactionHex,
                e);
            return new HttpResponseData<>(StringUtils.EMPTY,
                HttpReturnCode.WEID_SDK_ERROR.getCode(),
                HttpReturnCode.WEID_SDK_ERROR.getCodeDesc().concat(e.getMessage()));
        }
    }

    private WeIdAuthentication buildWeIdAuthority(String weIdPrivateKeyStr, String weId) {

        WeIdPrivateKey weIdPrivateKey = new WeIdPrivateKey();
        weIdPrivateKey.setPrivateKey(weIdPrivateKeyStr);

        WeIdAuthentication weIdAuthentication = new WeIdAuthentication();
        weIdAuthentication.setWeId(weId);
        weIdAuthentication.setWeIdPrivateKey(weIdPrivateKey);
        return weIdAuthentication;
    }

    /**
     * Query CPT via the InvokeFunction API.
     *
     * @param queryArgs the query arg
     * @return the CPT data
     */
    public HttpResponseData<Object> queryCptInvoke(InputArg queryArgs) {
        try {
            JsonNode cptIdNode = new ObjectMapper()
                .readTree(queryArgs.getFunctionArg())
                .get(ParamKeyConstant.CPT_ID);
            if (cptIdNode == null || StringUtils
                .isEmpty(InputUtil.removeDoubleQuotes(cptIdNode.toString()))) {
                return new HttpResponseData<>(null, HttpReturnCode.INPUT_NULL);
            }
            Integer cptId;
            try {
                cptId = Integer.valueOf(InputUtil.removeDoubleQuotes(cptIdNode.toString()));
            } catch (Exception e) {
                return new HttpResponseData<>(null, HttpReturnCode.VALUE_FORMAT_ILLEGAL);
            }
            ResponseData response;
            try {
                response = cptService.queryCpt(cptId);
            } catch (Exception e) {
                logger.error(
                    "[queryCpt]: unknow error. cptId:{}.",
                    queryArgs,
                    e);
                return new HttpResponseData<>(null, HttpReturnCode.WEID_SDK_ERROR);
            }
            return new HttpResponseData<>(
                InputUtil.convertJsonToSortedMap(JsonUtil.objToJsonStr(response.getResult())),
                response.getErrorCode(),
                response.getErrorMessage());
        } catch (Exception e) {
            logger.error(
                "[queryCpt]: unknow error. cptId:{}.",
                queryArgs,
                e);
            return new HttpResponseData<>(null, HttpReturnCode.UNKNOWN_ERROR.getCode(),
                HttpReturnCode.UNKNOWN_ERROR.getCodeDesc().concat(e.getMessage()));
        }
    }

}
