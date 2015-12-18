package model;

/**
 * Created by nfaure on 11/11/15.
 */
public class LeaderBoardEntry {

    private String category;
    private Double count;

    public LeaderBoardEntry(String category,Double count){
        this.setCategory(category);
        this.setCount(count);
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getCount() {
        return count;
    }

    public void setCount(Double count) {
        this.count = count;
    }
}
