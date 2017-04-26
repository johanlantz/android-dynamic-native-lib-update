import android.content.Context;
import android.text.TextUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Helper class to allow dynamic updates of shared native libraries.
 * Created by johan on 24/04/2017.
 */

public class NativeLibraryManager {
    private static final String TAG = NativeLibraryManager.class.getSimpleName();
    private static boolean cancelDownload = false;

    /**
     * @param bundledLibName        Name of the library provided with the app, without lib or .so.
     *                              For instance libmath.so would be only math.
     * @param optionalLibName       Name of the library we would prefer to use if it has been made
     *                              available through a dynamic download.
     * @param applicationFilesDir   Files directory of the app as returned by context.getFilesDir()
     *                              Since Context can not be used in a static method a higher
     *                              level init function must provide this parameter when this class
     *                              is initialized.
     */
    public static boolean loadDynamicLibrary(String bundledLibName, String optionalLibName,
                                             String applicationFilesDir) {
        boolean libLoaded = false;

        if (!TextUtils.isEmpty(optionalLibName)) {
            try {
                System.load(applicationFilesDir + "/" + "lib" + optionalLibName + ".so");
                libLoaded = true;
                Log.e(TAG, "Successfully loaded dynamically downloaded library: " + optionalLibName);
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "UnsatisfiedLinkError: Failed to load desired library: " + optionalLibName + " will use packaged library. Exception: " + e);
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException: Failed to load desired library: " + optionalLibName + " will use packaged library. Exception: " + e);
            } catch (NullPointerException e) {
                Log.e(TAG, "NullPointerException: Failed to load desired library: " + optionalLibName + " will use packaged library. Exception: " + e);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load desired library: " + optionalLibName + " will use packaged library. Exception: " + e);
            }
        }

        if (libLoaded == false) {
            try {
                System.loadLibrary(bundledLibName);
                Log.e(TAG, "Successfully loaded the bundled lib: " + bundledLibName);
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "Failed to load the bundled lib: " + bundledLibName + " with exception: " + e);
            }
        }

        return libLoaded;
    }

    /**
     *
     * @param libraryName       Complete name of the .so file to download
     * @param libraryUrl        Where to download the library
     * @param libraryChecksum   Optional MD5 checksum to verify a completed download
     * @param mContext          Application context
     */
    public static void downloadUpdatedLibraryIfNeeded(String libraryName, String libraryUrl,
                                                      String libraryChecksum, Context mContext) {
        try {
            FileInputStream file = mContext.openFileInput(libraryName);
            file.close();
            Log.d(TAG, libraryName + " found locally, no need to download again.");
            return;
        }
        catch(FileNotFoundException e) {
            Log.d(TAG, "Library " + libraryName + " not found locally, initiate download from " + libraryUrl);
        } catch (IOException e) {
            Log.d(TAG, "File close threw an exception but since the .so file was available no further action is taken");
            return;
        }

        Log.d(TAG, "Initiate download of library: " + libraryName);
        cancelDownload = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream input = null;
                OutputStream output = null;
                HttpURLConnection connection = null;
                String tempFileName = libraryName + ".temp";

                try {
                    URL url = new URL(libraryUrl + libraryName);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.connect();

                    // expect HTTP 200 OK, so we don't mistakenly save error report
                    // instead of the file
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        Log.d(TAG,"Server returned HTTP " + connection.getResponseCode()
                                + " " + connection.getResponseMessage());
                        return;
                    }

                    // this will be useful to display download percentage
                    // might be -1 if server did not report the length
                    int fileLength = connection.getContentLength();

                    // download the file
                    input = connection.getInputStream();
                    output = mContext.openFileOutput(tempFileName, Context.MODE_PRIVATE);

                    byte data[] = new byte[4096];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        if (cancelDownload) {
                            Log.d(TAG, "Canceling download of: " + libraryName);
                            break;
                        }
                        total += count;

                        if (fileLength > 0) // only if total length is known
                            Log.d(TAG, "Downloaded: " + ((int) (total * 100 / fileLength)) + " of:" + libraryName);
                        output.write(data, 0, count);
                    }

                    //If the download completes, check MD5 and rename the temp file
                    if (!cancelDownload) {
                        File tempFile = new File(mContext.getFilesDir(), tempFileName);
                        boolean checksumOk = MD5.checkMD5(libraryChecksum, tempFile);
                        if (checksumOk || libraryChecksum == null) {
                            Log.d(TAG, "Checksum " + (libraryChecksum != null ? "ok" : "not provided"));
                            File updatedFile = new File(mContext.getFilesDir(), libraryName);
                            tempFile.renameTo(updatedFile);
                            Log.d(TAG, "Download of " + libraryName + " completed and checksum is ok. The new library is ready to be used.");
                        } else {
                            Log.e(TAG, "Error: Checksum failed. We can not use this library");
                            tempFile.delete();
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Download failed");
                } finally {
                    try {
                        if (output != null)
                            output.close();
                        if (input != null)
                            input.close();
                    } catch (IOException ignored) {
                    }

                    if (connection != null)
                        connection.disconnect();
                }
            }
        }).start();
    }

    /**
     * Cancels all ongoing downloads
     */
    public static void cancelDownload() {
        Log.d(TAG, "Canceling all downloads");
        cancelDownload = true;
    }

    /**
     * MD5 calculator from the CyanogenMod project (GPL)
     */
    public static class MD5 {
        private static final String TAG = "MD5";

        public static boolean checkMD5(String md5, File updateFile) {
            if (TextUtils.isEmpty(md5) || updateFile == null) {
                Log.e(TAG, "MD5 string empty or updateFile null");
                return false;
            }

            String calculatedDigest = calculateMD5(updateFile);
            if (calculatedDigest == null) {
                Log.e(TAG, "calculatedDigest null");
                return false;
            }

            Log.d(TAG, "Calculated digest: " + calculatedDigest);
            Log.d(TAG, "Provided digest: " + md5);

            return calculatedDigest.equalsIgnoreCase(md5);
        }

        public static String calculateMD5(File updateFile) {
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "Exception while getting digest: " + e);
                return null;
            }

            InputStream is;
            try {
                is = new FileInputStream(updateFile);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Exception while getting FileInputStream: " + e);
                return null;
            }

            byte[] buffer = new byte[8192];
            int read;
            try {
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
                byte[] md5sum = digest.digest();
                BigInteger bigInt = new BigInteger(1, md5sum);
                String output = bigInt.toString(16);
                // Fill to 32 chars
                output = String.format("%32s", output).replace(' ', '0');
                return output;
            } catch (IOException e) {
                throw new RuntimeException("Unable to process file for MD5", e);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e(TAG, "Exception on closing MD5 input stream: " + e);
                }
            }
        }
    }
}
