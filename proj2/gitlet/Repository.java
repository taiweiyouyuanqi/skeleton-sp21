package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import static gitlet.Utils.*;

public class Repository implements Serializable {

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");

    public static void init(){
        //如果已经存在
        if(GITLET_DIR.exists()){
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            return;
        }

        //初始化文件夹
        GITLET_DIR.mkdirs();
        File obfile=new File(GITLET_DIR,"objects");
        obfile.mkdirs();
        File reffile=new File(GITLET_DIR,"refs");
        reffile.mkdirs();
        File headfile=new File(reffile,"heads");
        headfile.mkdirs();

        //初始commit
        Date currenttime=new Date(0);
        Commit initial=new Commit("initial commit",currenttime,null,null);
        String uid=initial.getUid();
        File write=new File(obfile,uid);
        writeObject(write,initial);

        //创建HEAD
        File Head=new File(GITLET_DIR,"HEAD");
        Utils.writeContents(Head,"ref: refs/heads/master");

        //master
        File masfile=new File(headfile,"master");

        //写初始提交uid
        Utils.writeContents(masfile,uid);

        //初始化暂存区
        File addfile=new File(GITLET_DIR,"addstage");
        File removefile=new File(GITLET_DIR,"removestage");
        Utils.writeObject(addfile,new TreeMap<String,String>());
        Utils.writeObject(removefile,new TreeMap<String,String>());

    }

    public static void add(String file){
        //拼接路径
        File now=join(CWD,file);

        //如果不存在
        if(!now.exists()){
            System.out.println("File does not exist.");
            return;
        }

        //读取工作区，创建blob
        Blob b=new Blob(now);
        String id=b.getUID();

        //如果有了，不用重复加
        TreeMap<String,String>add=getAdd();

        //如果之前在removestage
        File removefile=new File(GITLET_DIR,"removestage");
        TreeMap<String,String>rstage=readObject(removefile,TreeMap.class);
        if(rstage.containsKey(file)){
            rstage.remove(file);
            Utils.writeObject(removefile,rstage);
        }

        Commit cur = curCommit();
        if (id.equals(cur.getTracked().get(file))) {
            if (add.containsKey(file)) {
                add.remove(file);
                Utils.writeObject(new File(GITLET_DIR, "addstage"), add);
            }
            return;
        }

        if(id.equals(add.get(file))){
            return;
        }


        //保存blob
        File obj=new File(GITLET_DIR,"objects");
        File bfile=new File(obj,id);
        Utils.writeObject(bfile,b);

        //必须先读取暂存区原来的数据防止丢失
        File temp=new File(GITLET_DIR,"addstage");

        //新加入add的文件
        add.put(file,id);

        //全部写回
        Utils.writeObject(temp,add);
    }

    public static void commit(String message){
        if(message==null|| message.isEmpty()){
            System.out.println("Please enter a commit message.");
            return;
        }

        //打开add暂存区取出出数据
        File stagefile=new File(GITLET_DIR,"addstage");
        TreeMap<String,String>stage=Utils.readObject(stagefile, TreeMap.class);

        //删除removestage
        File removefile=new File(GITLET_DIR,"removestage");
        TreeMap<String,String>rstage=Utils.readObject(removefile, TreeMap.class);
        if(stage.isEmpty()&& rstage.isEmpty()){
            System.out.println("No changes added to the commit.");
            return;
        }

        //读取head
        File headfile=new File(GITLET_DIR,"HEAD");
        String headcontent=Utils.readContentsAsString(headfile);

        //得到分支
        String branch=headcontent.substring("ref: refs/heads/".length());
        File branchfile=new File(GITLET_DIR,"refs/heads/"+branch);

        String parent=Utils.readContentsAsString(branchfile);

        //拿到父亲哈希
        File fafile=new File(join(GITLET_DIR,"objects"),parent);
        Commit facommit=Utils.readObject(fafile, Commit.class);
        TreeMap<String,String>fatrack=new TreeMap<>(facommit.getTracked());

        //合并暂存区与父亲
        for(Map.Entry<String,String>entry:stage.entrySet()){
            fatrack.put(entry.getKey(),entry.getValue());
        }
        for (String removedFile : rstage.keySet()) {
            fatrack.remove(removedFile);
        }
        Utils.writeObject(removefile, new TreeMap<String, String>());
        Date current=new Date();

        //创建提交
        Commit newcommit=new Commit(message,current,findparent(parent),fatrack);
        String newid=newcommit.getUid();
        File commitfile=new File(join(GITLET_DIR,"objects"),newid);
        Utils.writeObject(commitfile,newcommit);
        Utils.writeContents(branchfile, newid);
        Utils.writeObject(stagefile, new TreeMap<String, String>());
    }

    public static void rm(String filename){
        File addfile=new File(GITLET_DIR,"addstage");
        File removefile=new File(GITLET_DIR,"removestage");
        TreeMap<String,String>addstage=Utils.readObject(addfile, TreeMap.class);
        TreeMap<String,String>removestage=Utils.readObject(removefile,TreeMap.class);



        //查看commit
        Commit curcommit=curCommit();
        boolean inStage = addstage.containsKey(filename);
        boolean isTracked = curcommit.getTracked().containsKey(filename);

        if (!inStage && !isTracked) {
            System.out.println("No reason to remove the file.");
            return;
        }

        if (inStage) {
            addstage.remove(filename);
            Utils.writeObject(addfile, addstage);
        }

        if (isTracked) {
            if (!removestage.containsKey(filename)) {
                removestage.put(filename, "REMOVE");
                Utils.writeObject(removefile, removestage);
            }
            File workingFile = new File(CWD, filename);
            if (workingFile.exists()) {
                workingFile.delete();
            }
        }
    }

    public static void log(){
        Commit temp=curCommit();
        while(temp != null){
            System.out.println("===");
            System.out.println("commit "+temp.getUid());
            if (temp.getParent() != null && temp.getParent().size() > 1) {
                String p1 = temp.getParent().get(0).substring(0, 7);
                String p2 = temp.getParent().get(1).substring(0, 7);
                System.out.println("Merge: " + p1 + " " + p2);
            }
            System.out.println("Date: "+temp.getDate());
            System.out.println(temp.getMessage());
            System.out.println();
            List<String>fa=temp.getParent();
            if (fa.isEmpty()) {
                break; // 到达初始提交，停止循环
            }
            String faid=fa.get(0);
            File fafile=new File(join(GITLET_DIR,"objects"),faid);
            temp=Utils.readObject(fafile, Commit.class);
        }
    }

    public static void status(){
        //打印分支
        println("=== Branches ===");
        File branch=new File(join(GITLET_DIR,"refs"),"heads");
        List<String>branches=Utils.plainFilenamesIn(branch);
        String branchcontent=Utils.readContentsAsString(new File(GITLET_DIR,"HEAD"));
        String curbranch=branchcontent.substring("ref: refs/heads/".length()).trim();
        Collections.sort(branches);
        for(String b:branches){
            if(b.equals(curbranch)){
                println("*"+b);
            }else{
                println(b);
            }
        }
        println("");

        //暂存区
        println("=== Staged Files ===");
        File stagefile=new File(GITLET_DIR,"addstage");
        TreeMap<String,String>f=Utils.readObject(stagefile,TreeMap.class);
        for(String name:f.keySet()){
            println(name);
        }
        println("");

        //移除文件
        println("=== Removed Files ===");
        File rmfile=new File(GITLET_DIR,"removestage");
        TreeMap<String,String>r=Utils.readObject(rmfile,TreeMap.class);
        for(String name:r.keySet()){
            println(name);
        }
        println("");
        println("=== Modifications Not Staged For Commit ===");
        println("");
        println("=== Untracked Files ===");
    }

    public static void checkout(String []args){
        if(args.length==3){
            if(args[1].equals("--")){
                Commit cur=curCommit();
                if(cur.getTracked().containsKey(args[2])){
                    String f=cur.getTracked().get(args[2]);
                    File repre=new File(join(GITLET_DIR,"objects"),f);
                    Blob blob=Utils.readObject(repre, Blob.class);
                    Utils.writeContents(new File(CWD,args[2]),blob.getContents());

                }else{
                    System.out.println("File does not exist in that commit.");
                }
            }else{
                System.out.println("Incorrect operands.");
            }
        }else if(args.length==4){
            if(args[2].equals("--")){
                String hashid=resolveId(args[1]);
                if (hashid == null) {
                    System.out.println("No commit with that id exists.");
                    return;
                }
                File wantfile=new File(join(GITLET_DIR,"objects"),hashid);
                if (!wantfile.exists()) {
                    System.out.println("No commit with that id exists.");
                    return;
                }
                Commit wantid=Utils.readObject(wantfile,Commit.class);
                Map<String,String>wantmap=wantid.getTracked();

                if(wantmap.containsKey(args[3])){

                    File f=new File(join(GITLET_DIR,"objects"),wantmap.get(args[3]));
                    Blob blob=Utils.readObject(f, Blob.class);
                    Utils.writeContents(new File(CWD,args[3]),blob.getContents());
                }else{
                    System.out.println("File does not exist in that commit.");
                }
            }else{
                System.out.println("Incorrect operands.");
            }
        }else if(args.length==2){
            String bname=args[1];
            File bfile=new File(join(GITLET_DIR,"refs","heads"),bname);

            //看看commit是否存在
            if(!bfile.exists()){
                System.out.println("No such branch exists.");
                return;
            }

            String commitId = Utils.readContentsAsString(bfile);
            Commit bcommit = Utils.readObject(new File(join(GITLET_DIR, "objects"), commitId), Commit.class);

            //如果commit是当前commit
            String curBranch = Utils.readContentsAsString(new File(GITLET_DIR, "HEAD"));
            if(curBranch.equals("ref: refs/heads/" + args[1])){
                System.out.println("No need to checkout the current branch.");
                return;
            }

            //是否有未跟踪文件
            TreeMap<String,String>a=getAdd();
            TreeMap<String,String>r=getRemove();
            for(String filename:bcommit.getTracked().keySet()){
                File cwd=new File(CWD,filename);
                if(cwd.exists()){
                    boolean tracked=curCommit().getTracked().containsKey(filename);
                    boolean instage=a.containsKey(filename)||r.containsKey(filename);
                    if(!tracked&&!instage){
                        System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                        return;
                    }
                }
            }

            //清理暂存区
            clean();

            //删除当前追踪但是目标commit未追踪的文件
            Commit curcommit=curCommit();
            for(String name:curcommit.getTracked().keySet()){
                if(!bcommit.getTracked().containsKey(name)){
                    //删除工作区文件
                    File workfile=new File(CWD,name);
                    if(workfile.exists()){
                        workfile.delete();
                    }
                }
            }

            //写入工作区
            for(Map.Entry<String,String> entry:bcommit.getTracked().entrySet()){
                String name=entry.getKey();
                String blob=entry.getValue();
                File newfile=new File(join(GITLET_DIR,"objects"),blob);
                Blob b=Utils.readObject(newfile,Blob.class);
                File workfile=new File(CWD,name);
                Utils.writeContents(workfile,b.getContents());
            }

            //更新HEAD
            File headfile=new File(GITLET_DIR,"HEAD");
            Utils.writeContents(headfile,"ref: refs/heads/"+args[1]);
        }else{
            System.out.println("Incorrect operands.");
        }
    }

    public static void gLog(){
        File g=new File(GITLET_DIR,"objects");
        List<String>gl=Utils.plainFilenamesIn(g);

        if(gl==null)return;

        for(String id:gl){
            File curfile=new File(join(GITLET_DIR,"objects"),id);
            Serializable obj = Utils.readObject(curfile, Serializable.class);
            if (obj instanceof Commit) {
                Commit cur = (Commit) obj;
                System.out.println("===");
                System.out.println("commit " + cur.getUid());
                System.out.println("Date: " + cur.getDate());
                System.out.println(cur.getMessage());
                System.out.println();
            }
        }
    }

    public static void find(String message){
        File g=new File(GITLET_DIR,"objects");
        List<String>gl=Utils.plainFilenamesIn(g);

        if(gl==null)return;
        boolean found = false;
        for(String id:gl){
            File curfile=new File(join(GITLET_DIR,"objects"),id);
            Serializable obj = Utils.readObject(curfile, Serializable.class);
            if (obj instanceof Commit) {
                Commit cur = (Commit) obj;

                if (message.equals(cur.getMessage())) {
                    System.out.println(cur.getUid());
                    found = true;
                }
            }
        }
        if (!found) {
            System.out.println("Found no commit with that message.");
        }
    }

    public static void branch(String bname){
        if (bname.equals("HEAD")) {
            System.out.println("A branch with that name already exists."); // 或类似错误
            return;
        }
        if(bname==null){
            System.out.println("Incorrect operands.");
            return;
        }
        File branchfile=new File(join(GITLET_DIR,"refs","heads"),bname);

        //看看是否已经有同名分支
        if(branchfile.exists()){
            System.out.println("A branch with that name already exists.");
            return;
        }
        Commit curcommit=curCommit();
        String bid=curcommit.getUid();
        Utils.writeContents(branchfile,bid);
    }

    public static void rmBranch(String branchname){
        File rfile=new File(join(GITLET_DIR,"refs","heads"),branchname);
        if(!rfile.exists()){
            System.out.println("A branch with that name does not exist.");
            return;
        }
        File curfile=new File(GITLET_DIR,"HEAD");
        String curname=Utils.readContentsAsString(curfile).substring("ref: refs/heads/".length()).trim();

        //如果是当前分支
        if(curname.equals(branchname)){
            System.out.println("Cannot remove the current branch.");
            return;
        }

        rfile.delete();
    }

    public static void reset(String id){
        String hashid=resolveId(id);
        if (hashid == null) {
            System.out.println("No commit with that id exists.");
            return;
        }
        File bfile=new File(join(GITLET_DIR,"objects"),hashid);
        if(!bfile.exists()){
            System.out.println("No commit with that id exists.");
            return;
        }

        Commit bcommit = Utils.readObject(bfile, Commit.class);

        //是否有未跟踪文件
        TreeMap<String,String>a=getAdd();
        TreeMap<String,String>r=getRemove();
        for(String filename:bcommit.getTracked().keySet()){
            File cwd=new File(CWD,filename);
            if(cwd.exists()){
                boolean tracked=curCommit().getTracked().containsKey(filename);
                boolean instage=a.containsKey(filename)||r.containsKey(filename);
                if(!tracked&&!instage){
                    System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                    return;
                }
            }
        }

        //删除当前追踪但是目标commit未追踪的文件
        Commit curcommit=curCommit();
        for(String name:curcommit.getTracked().keySet()){
            if(!bcommit.getTracked().containsKey(name)){
                //删除工作区文件
                File workfile=new File(CWD,name);
                if(workfile.exists()){
                    workfile.delete();
                }
            }
        }

        //写入工作区
        for(Map.Entry<String,String> entry:bcommit.getTracked().entrySet()){
            String name=entry.getKey();
            String blob=entry.getValue();
            File newfile=new File(join(GITLET_DIR,"objects"),blob);
            Blob b=Utils.readObject(newfile,Blob.class);
            File workfile=new File(CWD,name);
            Utils.writeContents(workfile,b.getContents());
        }

        //当前分支改commitid
        String headcontent=Utils.readContentsAsString(new File(GITLET_DIR,"HEAD"));
        String branchname=headcontent.substring("ref: refs/heads/".length()).trim();
        File branchfile=new File(join(GITLET_DIR,"refs","heads"),branchname);
        Utils.writeContents(branchfile,hashid);

        //清理暂存区
        clean();
    }

    public static void merge(String bname){
        //检查暂存区
        TreeMap<String,String>addstage=getAdd();
        TreeMap<String,String>removestage=getRemove();
        if(!addstage.isEmpty()||!removestage.isEmpty()){
            System.out.println("You have uncommitted changes.");
            return;
        }
        File bfile=new File(join(GITLET_DIR,"refs","heads"),bname);
        if(!bfile.exists()){
            System.out.println("A branch with that name does not exist.");
            return;
        }
        File head=new File(GITLET_DIR,"HEAD");
        String headcontent=Utils.readContentsAsString(head).substring("ref: refs/heads/".length()).trim();
        if(headcontent.equals(bname)){
            System.out.println("Cannot merge a branch with itself.");
            return;
        }
        Commit curcommit=curCommit();
        Commit branchcommit=branchCommit(bname);

        //未追踪文件处理
        for(String name:branchcommit.getTracked().keySet()){
            File cwd = new File(CWD, name);
            if(cwd.exists()
                    &&!curcommit.getTracked().containsKey(name)
                    &&!addstage.containsKey(name)
                    &&!removestage.containsKey(name)){
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return;
            }
        }

        //寻找祖先bfs
        Set<String>ancestor=new HashSet<>();
        Queue<Commit>queue=new LinkedList<>();
        queue.add(curcommit);
        while(!queue.isEmpty()){
            Commit t=queue.poll();
            if(ancestor.contains(t.getUid()))continue;
            ancestor.add(t.getUid());
            if(t.getParent()==null)continue;
            for(String parentid:t.getParent()){
                queue.add(Utils.readObject(new File(join(GITLET_DIR,"objects"),parentid), Commit.class));
            }
        }

        //公共祖先
        Commit ances=null;
        Queue<Commit>q2=new LinkedList<>();
        Set<String>ances2=new HashSet<>();
        q2.add(branchcommit);
        while(!q2.isEmpty()){
            Commit t=q2.poll();
            if(ances2.contains(t.getUid()))continue;
            if(ancestor.contains(t.getUid())){
                //找到祖先，退出
                ances=t;
                break;
            }
            ances2.add(t.getUid());
            if(t.getParent()==null)continue;
            for(String pid:t.getParent()){
                q2.add(Utils.readObject(new File(join(GITLET_DIR,"objects"),pid), Commit.class));
            }
        }

        //防御性编程，一般不会发生
        if(ances==null)return;

        //1.1如果给定的分支与分割点是同一个提交
        if(branchcommit.getUid().equals(ances.getUid())){
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }

        //1.2如果分割点是当前分支
        if(ances.getUid().equals(curcommit.getUid())){
            Utils.writeContents(new File(join(GITLET_DIR,"refs","heads"),headcontent),branchcommit.getUid());

            //同步工作区：删除当前有、目标没有的文件
            for(String name:curcommit.getTracked().keySet()){
                if(!branchcommit.getTracked().containsKey(name)){
                    File f=new File(CWD,name);
                    if(f.exists())f.delete();
                }
            }

            //同步工作区：写入目标分支的所有文件
            for(Map.Entry<String,String>entry:branchcommit.getTracked().entrySet()){
                String name=entry.getKey();
                Blob b=getBlob(entry.getValue());
                if(b!=null)Utils.writeContents(new File(CWD,name),b.getContents());
            }

            //清理暂存区
            clean();
            System.out.println("Current branch fast-forwarded.");
            return;
        }

        /*合并
          构建并集
         */
        Set<String>allfiles=new HashSet<>();
        allfiles.addAll(curcommit.getTracked().keySet());
        allfiles.addAll(branchcommit.getTracked().keySet());
        allfiles.addAll(ances.getTracked().keySet());
        List <String>p=new LinkedList<>();
        p.add(curcommit.getUid());
        p.add(branchcommit.getUid());
        TreeMap<String,String>mergemap=new TreeMap<>();

        boolean hasConflict = false;

        //遍历集合
        Set<String> conflicted = new HashSet<>();
        for(String name:allfiles){
            String curblob=curcommit.getTracked().get(name);
            String ancesblob=ances.getTracked().get(name);
            String branchblob=branchcommit.getTracked().get(name);

            //存在于分割点
            if(ancesblob!=null){
                //2自分割点来，当前branch没修改过，在给定 branch 中修改的，直接覆盖，然后自动暂存
                if(as(curblob,ancesblob)&&!as(branchblob, ancesblob)){
                    if(branchblob!=null) mergemap.put(name,branchblob);
                }
                //3当前branch修改，给定branch未修改，不动
                else if(!as(curblob,ancesblob)&&as(branchblob,ancesblob)){
                    if(curblob!=null)mergemap.put(name,curblob);
                }
                //debug加：都没改
                else if(as(curblob,ancesblob)&&as(branchblob,ancesblob)){
                    if(curblob!=null)mergemap.put(name,curblob);
                }
                //4+5相同方式修改，不动（都删除了的话，也不动）;给定branch未修改，当前branch不存在，也被删除
                else if(!as(curblob,ancesblob)&&!as(branchblob,ancesblob)&&as(curblob,branchblob)||curblob==null&&branchblob==null){
                    if(curblob!=null)mergemap.put(name,curblob);
                }
                //6存在于分割点，当前branch未修改，给定branch不存在的，被删除(无需写)
                //7不同方式修改
                else if(!as(curblob,ancesblob)&&!as(branchblob,ancesblob)&&!as(curblob,branchblob)){
                    String curcontent=(curblob==null)?"":new String(getBlob(curblob).getContents());
                    String branchcontent=(branchblob==null)?"":new String(getBlob(branchblob).getContents());
                    String content="<<<<<<< HEAD\n" +
                            curcontent +
                            "=======\n" +
                            branchcontent +
                            ">>>>>>>\n";
                    Utils.writeContents(new File(CWD,name),content);
                    File conflictFile = new File(CWD, name);
                    Blob cb = new Blob(conflictFile);
                    String cbHash = cb.getUID();
                    Utils.writeObject(new File(join(GITLET_DIR, "objects"), cbHash), cb);
                    mergemap.put(name, cbHash);
                    conflicted.add(name);
                    hasConflict = true;
                }
            } else{
                ////分割点不存在
                //debug:两边各自新增了同名文件、内容不同
                if (curblob != null && branchblob != null) {
                    if (!as(curblob, branchblob)) {
                        // 两边都新增了同名文件，内容不同 → 冲突
                        String curcontent = new String(getBlob(curblob).getContents());
                        String branchcontent = new String(getBlob(branchblob).getContents());
                        String content = "<<<<<<< HEAD\n" + curcontent + "\n=======\n" + branchcontent + "\n>>>>>>>\n";
                        Utils.writeContents(new File(CWD, name), content);
                        conflicted.add(name);
                        hasConflict = true;
                    } else {
                        // 两边新增了同名文件，但内容一样 → 保留
                        mergemap.put(name, curblob);
                    }
                }

                //8存在当前分支的文件，不动
                if(curblob!=null){
                    mergemap.put(name,curblob);
                }

                //9同上
                else if(branchblob!=null){
                    mergemap.put(name,branchblob);
                }
            }
        }

        //清理工作区
        for(String n:curcommit.getTracked().keySet()){
            if(!mergemap.containsKey(n)&& !conflicted.contains(n)){
                File f=new File(CWD,n);
                if(f.exists()){
                    f.delete();
                }
            }
        }

        //写入工作区
        for(Map.Entry<String, String> entry : mergemap.entrySet()){
            String n=entry.getKey();
            String blobHash = entry.getValue();
            Blob b=getBlob(blobHash);
            if(b!=null){
                Utils.writeContents(new File(CWD,n),b.getContents());
            }
        }

        // 如果有冲突，打印并返回，不要创建 merge commit
        if (hasConflict) {
            System.out.println("Encountered a merge conflict.");
            clean();

        }

        //改写mergecommit
        String messa="Merged "+bname+" into "+getCurrentBranch()+".";
        Commit mergecommit=new Commit(messa,new Date(),p,mergemap);

        //存盘
        File mergeFile = new File(join(GITLET_DIR, "objects"), mergecommit.getUid());
        Utils.writeObject(mergeFile, mergecommit);

        //改指针
        String curbranch=getCurrentBranch();
        File branchfile=new File (join(GITLET_DIR,"refs","heads"),curbranch);
        Utils.writeContents(branchfile,mergecommit.getUid());

        //清空暂存区
        clean();
    }

    //缩写id寻找完整hash
    public static String resolveId(String id){
        File objf=new File(GITLET_DIR,"objects");

        //已经完整
        File direct=new File(objf,id);
        if(direct.exists())return id;

        List<String>files=Utils.plainFilenamesIn(objf);
        if(files==null)return null;

        String match=null;
        for (String f:files){
            if(f.startsWith(id)){
                Serializable obj=Utils.readObject(new File(objf,f),Serializable.class);
                if(obj instanceof Commit){
                    if(match!=null)return null;
                    match=f;
                }
            }

        }
        return match;
    }

    public static String getCurrentBranch() {
        File headFile = new File(GITLET_DIR, "HEAD");
        String headContent = Utils.readContentsAsString(headFile);
        return headContent.substring("ref: refs/heads/".length()).trim();
    }

    //看看两个字符串是否相等
    public static boolean as(String a,String b){
        return Objects.equals(a,b);
    }
    public static Blob getBlob(String name){
        File blobfile=new File(join(GITLET_DIR,"objects"),name);
        if(!blobfile.exists())return null;
        return Utils.readObject(blobfile, Blob.class);
    }
    public static Commit branchCommit(String bname){
        File branchfile=new File (join(GITLET_DIR,"refs","heads"),bname);
        String branchid=Utils.readContentsAsString(branchfile);
        Commit branch =Utils.readObject(join(GITLET_DIR,"objects",branchid), Commit.class);
        return branch;
    }
    //得到当前commit，为了简化，写成一个函数
    public static Commit curCommit(){
        File headfile=new File(GITLET_DIR,"HEAD");
        String headcontent=Utils.readContentsAsString(headfile);
        String headp=headcontent.substring("ref: refs/heads/".length()).trim();
        File head=new File(join(GITLET_DIR,"refs","heads"),headp);
        String commit=Utils.readContentsAsString(head);
        Commit curcommit=Utils.readObject(join(GITLET_DIR,"objects",commit),Commit.class);
        return curcommit;
    }

    public static List<String> findparent(String par){
        List<String> pass=new ArrayList<>();
        pass.add(par);
        return pass;
    }

    private static void println(String s) {
        System.out.print(s + "\n");
    }

    //得到add暂存区map
    public static TreeMap<String,String> getAdd(){
        File f=new File (GITLET_DIR,"addstage");
        TreeMap<String,String>a=Utils.readObject(f,TreeMap.class);
        return a;
    }

    //情况暂存区
    public static void clean(){
        Utils.writeObject(new File(GITLET_DIR, "addstage"), new TreeMap<String, String>());
        Utils.writeObject(new File(GITLET_DIR, "removestage"), new TreeMap<String, String>());
    }
    //得到remove暂存区map
    public static TreeMap<String,String> getRemove(){
        File f=new File (GITLET_DIR,"removestage");
        TreeMap<String,String>r=Utils.readObject(f,TreeMap.class);
        return r;
    }
}