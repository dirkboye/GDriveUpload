package ch.boye.GDriveUpload;

import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class GDriveUploadOptions {
    private GDriveUploadOptions() { }

    private String google_title = null;
    private String md5 = null;
    private int chunk_size_mb = 100;
    private String local_filename = null;
    private long file_size;
    private String mime_type;
    private String[] metadata_tags;
    private String metadata_id = null;



    private boolean use_old_api = true;


    // Make sure the ServerSettings Singleton instantiation is thread-safe
    private static class SingletonHolder {
        public static final GDriveUploadOptions INSTANCE = new GDriveUploadOptions();
    }

    public static GDriveUploadOptions getInstance() {
    return SingletonHolder.INSTANCE;
}

    public static void showHelp() {
        HelpFormatter h = new HelpFormatter();
        h.printHelp("GDriveUpload", CLIOptions());
        System.exit(-1);
    }

    public static Options CLIOptions() {
        Options options = new Options();
        options.addOption("help", false, "Show this help.");
        options.addOption("upload", true, "Upload local file to Google Drive. (<arg>=filename)");
        options.addOption("metadata", true, "Get metadata of file on Google Drive. (<arg>=id)");
        Option option = new Option("metatags", "Tags to show additionally in the end. ( multiple <arg> allowed. {a,b,c})");
        option.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(option);
        options.addOption("google_title", true, "Title of upload on Google Drive. If not set, local filename will be used.");
        options.addOption("chunk_size_mb", true, "Chunk size in megabytes.");
        options.addOption("use_new_api", false, "Use new Drive API. Uploads cannot last longer than 1 hour with the new API.");
        options.addOption("md5", true, "Provide md5sum so it doesn't have to be calculated.");
        options.addOption("log", true, "Log level. 0-3  (2 - Show Request Line, Response Status Line, 3 - Show full request/response)");
        return options;
    }


    public void parseArgs(String args[]) {
        Options options = GDriveUploadOptions.CLIOptions();
        CommandLineParser parser = new PosixParser();
        try {
            CommandLine cmd = parser.parse( options, args);
            if (cmd.hasOption("help")) {
                showHelp();
            }
            if (cmd.hasOption("google_title")) {
                google_title = cmd.getOptionValue("google_title");
            }
            if (cmd.hasOption("md5")) {
                md5 = cmd.getOptionValue("md5");
            }
            if (cmd.hasOption("metatags")) {
                metadata_tags = cmd.getOptionValues("metatags");
            }
            if (cmd.hasOption("use_new_api")) {
                use_old_api = false;
            }
            if (cmd.hasOption("metadata")) {
                metadata_id = cmd.getOptionValue("metadata");
            }
            if (cmd.hasOption("log")) {
                ConsoleLog.getInstance().setLogLevel(Integer.parseInt(cmd.getOptionValue("log")));
            }
            if (cmd.hasOption("chunk_size_mb")) {
                chunk_size_mb = Integer.parseInt(cmd.getOptionValue("chunk_size_mb"));
                if (chunk_size_mb > 2000) {
                    System.out.println("Chunk size must be smaller than 2000 Mb. Setting chunk_size_mb to 2000.");
                    chunk_size_mb = 2000;
                }
            }

            if (cmd.hasOption("upload")) {
                local_filename = cmd.getOptionValue("upload");
                File file = new File(local_filename);
                file_size = file.length();
                try {
                    mime_type = Files.probeContentType(Paths.get(local_filename));
                } catch (IOException e) {
                    mime_type = "application/octet-stream";
                }
                if (!cmd.hasOption("google_title")) {
                    google_title = file.getName();
                }
            }
        } catch (ParseException e) {
            showHelp();
        }
    }

    public String getGoogleTitle() {
        return google_title;
    }

    public String getMd5() {
        return md5;
    }

    public int getChunkSizeInBytes() {
        return chunk_size_mb * 1024 * 1024;
    }

    public String getLocalFilename() {
        return local_filename;
    }

    public long getLocalFileSize() {
        return file_size;
    }

    public String getMimeType() {
        return mime_type;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getMetadataId() {
        return metadata_id;
    }

    public String[] getMetadataTags() {
        return metadata_tags;
    }

    public boolean getUseOldAPI() {
        return use_old_api;
    }
}

