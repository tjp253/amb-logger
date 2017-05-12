//package uk.ac.nottingham.eaxtp1.CradleRideLogger;
//
//import android.app.IntentService;
//import android.content.Intent;
//
//import java.io.BufferedInputStream;
//import java.io.BufferedOutputStream;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.util.zip.ZipEntry;
//import java.util.zip.ZipOutputStream;
//
//import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.compressing;
//import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.folderPath;
//import static uk.ac.nottingham.eaxtp1.CradleRideLogger.MainActivity.zipPath;
//
//@SuppressWarnings("ResultOfMethodCallIgnored")
//public class CompressionService extends IntentService {
//    public CompressionService() { super("CompressionService");
//    }
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//
////        Ensures there's a folder to put the ZIPs in.
//        File zipDirectory = new File(zipPath);
//        if (!zipDirectory.exists()) {
//            zipDirectory.mkdir();
//        }
//
//    }
//
//    @Override
//    protected void onHandleIntent(Intent intent) {
//
//        zipFiles(folderPath, zipPath);
//
//    }
//
//    //    Zips all files in the specified folder, into a given folder.
//    public boolean zipFiles(String pathToFolder, String pathToZip) {
//
//        final int BUFFER = 2048;
//
//        File sourceFolder = new File(pathToFolder);
//        int sourceLength = sourceFolder.getParent().length();
//        sourceLength = sourceLength + 4;
//
//        File[] fileList = sourceFolder.listFiles();
//
//        for (File file : fileList) {
//
//            String unmodifiedFilePath = file.getPath();
//            String fileName = unmodifiedFilePath.substring(sourceLength);
//            String fileFolder = fileName.substring(0, fileName.length() - 3) + "zip";
//            pathToZip = pathToZip + fileFolder;
//
//            try {
//
//                FileInputStream fileInputStream = new FileInputStream(unmodifiedFilePath);
//                FileOutputStream zipDestination = new FileOutputStream(pathToZip);
//                ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(zipDestination));
//                BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream, BUFFER);
//
//                byte data[] = new byte[BUFFER];
//
//                ZipEntry zipEntry = new ZipEntry(fileName);
//                zipOut.putNextEntry(zipEntry);
//
//                int count;
//                while ((count = bufferedInputStream.read(data, 0, BUFFER)) != -1) {
//                    zipOut.write(data, 0, count);
//                }
//
//                file.delete();
//
//                bufferedInputStream.close();
//
//                zipOut.close();
//
//            } catch (Exception e) {
//                e.printStackTrace();
//                return false;
//            }
//
//        }
//
//        compressing = false;
//
//        return true;
//    }
//
//}