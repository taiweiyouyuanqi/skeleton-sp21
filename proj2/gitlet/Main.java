package gitlet;

public class Main {

    public static void main(String[] args) {
        if(args.length<1){
            System.out.println("Please enter a command.");
            return;
        }
        String firstArg = args[0];

        switch(firstArg) {
            case "init":
                Repository.init();
                break;

            case "add":
                if(!ensureInitialized())return;
                if (args.length != 2) { System.out.println("Incorrect operands."); return; }
                Repository.add(args[1]);
                break;

            case "commit":
                if(!ensureInitialized())return;
                String msg = args.length < 2 ? "" : args[1];
                Repository.commit(msg);
                break;

            case "rm":
                if(!ensureInitialized())return;
                if (args.length != 2) { System.out.println("Incorrect operands."); return; }
                Repository.rm(args[1]);
                break;

            case "log":
                if(!ensureInitialized())return;
                Repository.log();
                break;

            case "global-log":
                if(!ensureInitialized())return;
                Repository.gLog();
                break;

            case "find"://根据message查找
                if(!ensureInitialized())return;
                if (args.length != 2) { System.out.println("Incorrect operands."); return; }
                Repository.find(args[1]);
                break;

            case "status":
                if(!ensureInitialized())return;
                Repository.status();
                break;

            case "checkout":
                if(!ensureInitialized())return;
                Repository.checkout(args);
                break;

            case "branch":
                if(!ensureInitialized())return;
                if (args.length != 2) { System.out.println("Incorrect operands."); return; }
                Repository.branch(args[1]);
                break;

            case "rm-branch":
                if(!ensureInitialized())return;
                if (args.length != 2) { System.out.println("Incorrect operands."); return; }
                Repository.rmBranch(args[1]);
                break;

            case "reset":
                if(!ensureInitialized())return;
                if (args.length != 2) { System.out.println("Incorrect operands."); return; }
                Repository.reset(args[1]);
                break;

            case "merge":
                if(!ensureInitialized())return;
                if (args.length != 2) { System.out.println("Incorrect operands."); return; }
                Repository.merge(args[1]);
                break;

            default:
                System.out.println("No command with that name exists.");
        }
    }
    private static boolean ensureInitialized() {
        if (!Repository.GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            return false;
        }
        return true;
    }
}
