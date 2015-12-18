package controllers;

import com.github.davidmoten.geo.GeoHash;
import com.typesafe.config.Config;
import model.GeoHashCounter;
import model.LeaderBoardEntry;
import org.joda.time.DateTime;
import play.Logger;
import play.libs.F;
import play.libs.Json;
import play.mvc.*;

import redis.clients.jedis.*;
import views.html.*;

import javax.security.auth.login.Configuration;
import java.util.*;

import static java.lang.String.format;
import static play.Logger.error;
import static play.Logger.info;

public class Application extends Controller {


    public static final String CATEG_LEADERBOARD = "categ_leaderboard";
    private static JedisPool jedisPool = new JedisPool(new JedisPoolConfig(), play.Configuration.root().getString("redis.host"));

    public Result index() {
        return ok("pong");
    }


    public F.Promise<Result> addGeoLocation(){
        Double latitude = request().body().asJson().get("latitude").asDouble();
        Double longitude = request().body().asJson().get("longitude").asDouble();
        String category = request().body().asJson().get("category").asText();

        return sendToRedis(latitude, longitude, category);
    }

    public F.Promise<Result> getGeoLocation(final String geoHash){

        return F.Promise.promise(new F.Function0<Result>() {
            @Override
            public Result apply() throws Throwable {
                Jedis jedis = null;
                try {
                    jedis = jedisPool.getResource();
                    ScanParams params = new ScanParams();
                    String keyPrefix = DateTime.now().getMinuteOfDay() + ":";
                    params.match(keyPrefix + geoHash + "?");
                    ScanResult<String> scanResult = jedis.scan("0", params);
                    List<String> keys = scanResult.getResult();
                    String nextCursor = scanResult.getStringCursor();
                    int counter = 0;
                    Map<String, Long> geohashesMap = new HashMap<String, Long>();
                    while (true) {
                        for (String key : keys) {
                            geohashesMap.put(key.split(":")[1], Long.parseLong(jedis.get(key)));
                        }

                        // An iteration also ends at "0"
                        if (nextCursor.equals("0")) {
                            break;
                        }

                        scanResult = jedis.scan(nextCursor, params);
                        nextCursor = scanResult.getStringCursor();
                        keys = scanResult.getResult();
                    }
                    return ok(Json.toJson(sortByComparator(geohashesMap)));
                } catch (Exception e) {
                    error("Error", e);
                    return internalServerError();
                } finally {
                    jedisPool.returnResourceObject(jedis);
                }
            }
        });
    }


    public F.Promise<Result> getSearchHotTrends(final String siteId){
        return F.Promise.promise(new F.Function0<Result>() {
            @Override
            public Result apply() throws Throwable {
                Jedis jedis = null;
                try {
                    jedis = jedisPool.getResource();

                    Set<Tuple> tuples = jedis.zrevrangeWithScores(String.format("%s:SEARCH_QUERIES",siteId.toUpperCase()),0,100L);


                    List<String> entries = new ArrayList<String>();
                    for (Tuple tuple: tuples){
                        entries.add(new LeaderBoardEntry(tuple.getElement(),tuple.getScore()));
                    }
                    return ok(Json.toJson(entries));
                } catch (Exception e) {
                    error("Error", e);
                    return internalServerError();
                } finally {
                    jedisPool.returnResourceObject(jedis);
                }
            }
        });
    }


    public F.Promise<Result> findLeaderBoard(){
        return F.Promise.promise(new F.Function0<Result>() {
            @Override
            public Result apply() throws Throwable {
                Jedis jedis = null;
                try {
                    jedis = jedisPool.getResource();

                    Set<Tuple> tuples = jedis.zrevrangeWithScores(CATEG_LEADERBOARD,0,10L);

                    List<LeaderBoardEntry> entries = new ArrayList<LeaderBoardEntry>();
                    for (Tuple tuple: tuples){
                        entries.add(new LeaderBoardEntry(tuple.getElement(),tuple.getScore()));
                    }
                    return ok(Json.toJson(entries));
                } catch (Exception e) {
                    error("Error", e);
                    return internalServerError();
                } finally {
                    jedisPool.returnResourceObject(jedis);
                }
            }
        });
    }

    private List<GeoHashCounter> sortByComparator(Map<String, Long> unsortMap) {

        List<Map.Entry<String, Long>> list =
                new LinkedList<Map.Entry<String, Long>>(unsortMap.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<String, Long>>() {
            public int compare(Map.Entry<String, Long> o1,
                               Map.Entry<String, Long> o2) {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });

        List<GeoHashCounter> geoHashCounters = new ArrayList<GeoHashCounter>();
        for (Iterator<Map.Entry<String, Long>> it = list.iterator(); it.hasNext();) {
            Map.Entry<String, Long> entry = it.next();
            geoHashCounters.add(new GeoHashCounter(entry.getKey(), entry.getValue()));
        }
        return geoHashCounters;
    }

    private F.Promise<Result> sendToRedis(Double latitude, Double longitude, String category) {
        return F.Promise.promise(new F.Function0<Result>() {
            @Override
            public Result apply() throws Throwable {
                Jedis jedis=null;
                try{
                    jedis = jedisPool.getResource();

                    String geoHash = GeoHash.encodeHash(latitude,longitude).substring(0,7);
                    String currentKeyPrefix = DateTime.now().getMinuteOfDay() + ":";
                    String nextKeyPrefix = (DateTime.now().getMinuteOfDay() + 1) + ":";
                    for(int i=1;i<=7;i++){
                        String currentKey=currentKeyPrefix + geoHash.substring(0,i);
                        String nextKey=nextKeyPrefix + geoHash.substring(0,i);
                        jedis.incr(currentKey);
                        jedis.incr(nextKey);
                        jedis.expire(currentKey, 3600);
                        jedis.expire(nextKey,3600);
                    }

                    jedis.zincrby(CATEG_LEADERBOARD,1D,category);
                    info(format("GeoHash %s was incremented", geoHash));
                    return created();
                }catch(Exception e){
                    error("Error",e);
                    return internalServerError();
                }finally{
                    jedisPool.returnResourceObject(jedis);
                }
            }
        });
    }

}
