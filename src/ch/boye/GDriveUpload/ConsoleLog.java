package ch.boye.GDriveUpload;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

public class ConsoleLog {
        private static int log_level = 0;
        private ConsoleLog() { }

        private static class SingletonHolder {
            public static final ConsoleLog INSTANCE = new ConsoleLog();
        }

        public static ConsoleLog getInstance() {
            return SingletonHolder.INSTANCE;
        }

        public static void setLogLevel(int level) {
            log_level = level;
        }

        public static void log(String line) {
            if (log_level>-1) {
                System.out.println(line);
            }
        }

        public static void log1(String line) {
            if (log_level>0) {
                System.out.println(line);
            }
        }

        public static void log(HttpRequest req, BufferedHttpEntity entity) {
            if (log_level > 1) {
                System.out.println("----------------------");
                System.out.println("     HTTP Request");
                System.out.println("----------------------");
                System.out.println(req.getRequestLine().toString());
                if (log_level > 2) {
                    System.out.println("Headers:");
                    System.out.println("--------");
                    Header[] hdrs = req.getAllHeaders();
                    for (int i = 0; i < hdrs.length; ++i) {
                        System.out.println(hdrs[i].getName() + ": " + hdrs[i].getValue());
                    }
                    if (entity != null) {
                        try {
                            System.out.println("Entity:");
                            System.out.println("-------");
                            System.out.println(EntityUtils.toString(entity));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                System.out.println("");
            }
        }

        public static void log(HttpResponse response, BufferedHttpEntity entity) {
            if (log_level > 1) {
                System.out.println("------------------------");
                System.out.println("     HTTP Response:");
                System.out.println("------------------------");
                System.out.println("Status: " + response.getStatusLine());
                if (log_level > 2) {
                    System.out.println("Headers:");
                    System.out.println("--------");
                    Header[] hdrs = response.getAllHeaders();
                    for (int i = 0; i < hdrs.length; ++i) {
                        System.out.println(hdrs[i].getName() + ": " + hdrs[i].getValue());
                    }
                    if (entity != null) {
                        try {
                            System.out.println("Entity:");
                            System.out.println("-------");
                            System.out.println(EntityUtils.toString(entity));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                System.out.println("");
            }
        }
}
