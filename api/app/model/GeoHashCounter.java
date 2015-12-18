package model;

/**
 * Created by nfaure on 11/11/15.
 */
public class GeoHashCounter {

    private String geoHash;
    private Long count;

    public GeoHashCounter(String geoHash, Long count) {
        this.geoHash = geoHash;
        this.count = count;
    }

    public String getGeoHash() {
        return geoHash;
    }

    public void setGeoHash(String geoHash) {
        this.geoHash = geoHash;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
