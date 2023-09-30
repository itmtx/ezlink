package cn.itmtx.ddd.ezlink.domain.filter.custom;

import cn.itmtx.ddd.ezlink.domain.context.TransformContext;
import cn.itmtx.ddd.ezlink.domain.filter.TransformFilter;
import cn.itmtx.ddd.ezlink.domain.filter.TransformFilterChain;
import com.alibaba.cola.exception.BizException;
import com.alibaba.cola.exception.SysException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

/**
 * 抽象类，用于给各个过滤器进行命名
 */
@Slf4j
public abstract class BaseNamingTransformFilter implements TransformFilter {

    private final TransformFilter delegate;

    public BaseNamingTransformFilter(TransformFilter delegate) {
        this.delegate = delegate;
    }

    /**
     * 定义过滤器名称, 由子类实现
     *
     * @return String
     */
    public abstract String filterName();

    @Override
    public int order() {
        return delegate.order();
    }

    @Override
    public void init(TransformContext context) {
        // 调用各个 TransformFilter 自己的 init 方法
        delegate.init(context);
    }

    @Override
    public void doFilter(TransformFilterChain chain, TransformContext context) {
        if (log.isDebugEnabled()) {
            log.debug("Entry TransformFilter {}......", filterName());
        }
        long start = System.nanoTime();
        try {
            delegate.doFilter(chain, context);
        } catch (Exception e) {
            log.error("TransformFilter:{} do failed. " + e.getMessage(), filterName());
            throw new BizException("{} do failed. ", filterName());
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("Exit TransformFilter:{},execution cost {} ms...", filterName(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            }
        }
    }


}