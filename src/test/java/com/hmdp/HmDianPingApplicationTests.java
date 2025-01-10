package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import net.sf.jsqlparser.statement.select.KSQLJoinWindow;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
//    @Test
//    void testSaveShop()
//    {
//        long timeInMilliSec = new Date().getTime();
//        TimeUnit time =  TimeUnit.MILLISECONDS;
//        System.out.println("Time " + timeInMilliSec
//                + "millSeconds in seconds = " + time.toSeconds(timeInMilliSec)
//                    );
//    }
    @Test
    void testSavaShop(){
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L,shop,10L, TimeUnit.SECONDS);
    }
}
