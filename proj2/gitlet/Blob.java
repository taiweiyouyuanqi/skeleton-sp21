package gitlet;

import java.io.File;
import java.io.Serializable;
import static gitlet.Utils.readContents;
import static gitlet.Utils.sha1;

public class Blob implements Serializable {
    private byte[] contents;

    //add添加文件，生成blob
    public Blob(File file){
        this.contents=readContents(file);
    }

    public byte[] getContents() {
        return this.contents;
    }

    // 计算 Blob 的哈希值（UID）
    public String getUID() {
        return sha1(contents);
    }
}
