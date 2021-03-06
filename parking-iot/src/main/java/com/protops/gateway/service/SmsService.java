package com.protops.gateway.service;

import com.protops.gateway.constants.ResponseCodes;
import com.protops.gateway.domain.SmsInfo;
import com.protops.gateway.exception.ProtopsException;
import com.protops.gateway.util.StringUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpSession;
import java.text.MessageFormat;
import java.util.Date;

/**
 * Created by zhouzhihao on 2015/4/20.
 */
public class SmsService {

    private static final Logger logger = LoggerFactory.getLogger(SmsService.class);

    @Autowired
    SmsSender smsSender;

    private boolean onTestMode;

    private String template;

    private Integer maxLimitCnt;

    private Integer invalidTimespan;

    private Integer smsSentInterval;

    private static final String SMS_KEY = "smsKey";

    public boolean sendSms(String mobile, HttpSession httpSession) throws ProtopsException {

        boolean ok = true;
        try {

            SmsInfo smsInfo = (SmsInfo) httpSession.getAttribute(SMS_KEY);

            // 如果是空，创建一个
            if (smsInfo == null) {
                smsInfo = new SmsInfo(mobile);
            }

            smsIntervalCheck(smsInfo);

            smsCntCheck(smsInfo);

            String smsCode = RandomStringUtils.randomNumeric(6);

            String msg = MessageFormat.format(getTemplate(), smsCode);

            if (!isOnTestMode()) {
                ok = smsSender.send(mobile, msg);
            }

            if (ok) {

                smsInfo.finishSent(smsCode);

                httpSession.setAttribute(SMS_KEY, null);

            }

        } catch (ProtopsException e) {

            throw e;

        } catch (Exception e) {

            return false;

        }

        return ok;
    }

    private void smsIntervalCheck(SmsInfo smsInfo) throws ProtopsException {
        // 如果发送时间已经记录
        if (smsInfo.getLastSuccessSentTime() > 0) {

            long leftTime = ((new Date().getTime() - smsInfo.getLastSuccessSentTime()) / 1000);

            logger.warn("[smsInfo][mobile={}][{}]", smsInfo.getMobile(), leftTime);

            if (leftTime < getSmsSentInterval()) {
                throw new ProtopsException(ResponseCodes.Weixin.SMS_TOO_FREQUENCY);
            }

        }

    }

    public void smsCntCheck(SmsInfo smsInfo) throws ProtopsException {
        if (smsInfo.getTryCnt() != null && smsInfo.getTryCnt() > getMaxLimitCnt()) {
            throw new ProtopsException(ResponseCodes.Weixin.TOO_MANY_SMS_SENT);
        }
    }

    public void clearSentCnt(HttpSession httpSession) throws ProtopsException {
        httpSession.setAttribute(SMS_KEY, null);
    }

    public boolean validateSms(String smsCode, HttpSession httpSession) throws ProtopsException {

        // 在这里获取的smsInfo必须存在
        SmsInfo smsInfo = (SmsInfo) httpSession.getAttribute(SMS_KEY);

        if (smsInfo == null) {
            return false;
        }

        // 检查短信失效时间。当前时间减去发送成功时间就等于短信的验证间隔，短信的验证间隔如果比失效时间长，就再见
        long leftTime = ((new Date().getTime() - smsInfo.getLastSuccessSentTime()) / 1000);

        // 如果短信已失效
        long invalidTimespan = getInvalidTimespan() * 60;

        if (leftTime > invalidTimespan) {

            logger.warn("[validateSms][smsInvalid]");

            httpSession.setAttribute(SMS_KEY, null);

            throw new ProtopsException(ResponseCodes.Weixin.SMS_CODE_INVALID);
        }

        boolean ok = StringUtils.equals(smsCode, smsInfo.getSmsCode());

        if (isOnTestMode()) {
            return true;
        }

        return ok;
    }

    public boolean isOnTestMode() {
        return onTestMode;
    }

    public void setOnTestMode(boolean onTestMode) {
        this.onTestMode = onTestMode;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Integer getMaxLimitCnt() {
        return maxLimitCnt;
    }

    public void setMaxLimitCnt(Integer maxLimitCnt) {
        this.maxLimitCnt = maxLimitCnt;
    }


    public Integer getSmsSentInterval() {
        return smsSentInterval;
    }

    public void setSmsSentInterval(Integer smsSentInterval) {
        this.smsSentInterval = smsSentInterval;
    }

    public Integer getInvalidTimespan() {
        return invalidTimespan;
    }

    public void setInvalidTimespan(Integer invalidTimespan) {
        this.invalidTimespan = invalidTimespan;
    }
}