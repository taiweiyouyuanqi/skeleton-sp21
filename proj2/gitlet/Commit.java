package gitlet;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Commit implements Serializable {

    /** The message of this Commit. */
    private String message;
    //日期
    private Date date;
    private List<String>parent;
    //文件名
    private Map<String, String> tracked;

    public Commit(String message,Date date,List<String>parents,Map<String, String> tracked){
        this.message=message;
        this.date=date;
        if (parents == null) {
            this.parent = new ArrayList<>();
        } else {
            this.parent = new ArrayList<>(parents);
        }
        if (tracked == null) {
            this.tracked = new TreeMap<>();
        } else {
            this.tracked = new TreeMap<>(tracked);
        }
    }

    public String getMessage(){
        return this.message;
    }

    public String getDate(){
        return dateToDate(this.date);
    }

    public List<String> getParent(){
        return this.parent;
    }

    public Map<String,String> getTracked(){
        return this.tracked;
    }

    //规范化日期
    public String dateToDate(Date date){
        DateFormat dateFormat = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy -0000", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(date);
    }

    //得到commit哈希值
    public String getUid(){
        return Utils.sha1(dateToDate(this.date), this.message,this.parent.toString(),this.tracked.toString());
    }
}
