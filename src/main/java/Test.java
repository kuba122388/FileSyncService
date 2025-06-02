import common.utils.FileWorker;

import java.nio.file.Paths;
import java.util.Scanner;

public class Test {
    public static void main(String[] args){
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter directory");
        String x = scanner.nextLine();
        FileWorker fileWorker = new FileWorker(Paths.get(x));
        fileWorker.showFolderContent();
    }
}
