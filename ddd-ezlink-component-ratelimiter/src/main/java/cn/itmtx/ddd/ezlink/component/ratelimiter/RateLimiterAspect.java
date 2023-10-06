package cn.itmtx.ddd.ezlink.component.ratelimiter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.server.ServerWebExchange;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Aspect
@Component
@Slf4j
public class RateLimiterAspect {

    @Autowired
    private RedisScript<Long> limitScript;

    @Autowired
    private RedisTemplate redisTemplate;

    private static final String UNKNOWN_VALUE = "unknown";
    private static final String LOCALHOST_V0 = "0.0.0.0";
    private static final String LOCALHOST_V4 = "127.0.0.1";
    private static final String LOCALHOST_V6 = "0:0:0:0:0:0:0:1";

    private static final String X_FORWARDED_FOR_KEY = "X-Forwarded-For";
    private static final String PROXY_CLIENT_IP_KEY = "Proxy-Client-IP";
    private static final String WL_PROXY_CLIENT_IP_KEY = "WL-Proxy-Client-IP";
    private static final String HTTP_CLIENT_IP_KEY = "HTTP_CLIENT_IP";
    private static final String HTTP_X_FORWARDED_FOR_KEY = "HTTP_X_FORWARDED_FOR";

    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint joinPoint, RateLimiter rateLimiter) {
        String key = rateLimiter.key();
        int count = rateLimiter.count();
        int time = rateLimiter.time();

        String combineKey = buildKey(rateLimiter, joinPoint);
        List<String> keys = Collections.singletonList(combineKey);
        try {
            // 获取方法调用次数
            Long callCount = (Long) redisTemplate.execute(limitScript, keys, count, time);
            log.info("限流类型[{}], 限流次数[{}], 限流时间[{}], 当前请求次数[{}], 缓存key[{}]", rateLimiter.limitType(), count, time, callCount, combineKey);
            if (Objects.isNull(callCount) || callCount.intValue() > count) {
                throw new RateLimitException("访问过于频繁，请稍后再试!");
            }
        } catch (RateLimitException e) {
            log.warn(e.getMessage());
            throw new RateLimitException(e.getMessage());
        } catch (Exception e) {
            log.error("服务器限流异常! " + e);
            throw new RateLimitException("服务器限流异常，请稍候再试!");
        }
    }

    private String buildKey(RateLimiter rateLimiter, JoinPoint joinPoint) {
        StringBuffer key = new StringBuffer(rateLimiter.key());
        if (rateLimiter.limitType().equals(LimitType.IP)) {
            // 获取 ServerWebExchange 对象 (切面的第一个参数)
            ServerWebExchange exchange = (ServerWebExchange) joinPoint.getArgs()[0];
            // 获取客户端 IP
            String clientIp = IpUtils.X.extractClientIp(exchange.getRequest());
            key.append(clientIp).append(":");
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> declaringClass = method.getDeclaringClass();
        key.append(declaringClass.getName()).append(":").append(method.getName());

        return key.toString();
    }

}
