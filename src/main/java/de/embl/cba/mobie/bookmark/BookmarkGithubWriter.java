package de.embl.cba.mobie.bookmark;

import com.google.gson.GsonBuilder;
import com.google.gson.internal.LinkedTreeMap;
import de.embl.cba.tables.github.GitHubContentGetter;
import de.embl.cba.tables.github.GitHubFileCommitter;
import de.embl.cba.tables.github.GitLocation;
import ij.Prefs;
import ij.gui.GenericDialog;
import org.apache.commons.compress.utils.FileNameUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BookmarkGithubWriter {

    public static final String ACCESS_TOKEN = "MoBIE.GitHub access token";
    private String accessToken;
    private String bookmarkFileName;
    private GitLocation bookmarkGitLocation;
    private BookmarksJsonParser bookmarksJsonParser;

    BookmarkGithubWriter(GitLocation bookmarkGitLocation, BookmarksJsonParser bookmarksJsonParser) {
        this.bookmarkGitLocation = bookmarkGitLocation;
        this.bookmarksJsonParser = bookmarksJsonParser;
    }

    private Map< String, String > getFilePathsToSha()
    {
        final GitHubContentGetter contentGetter =
                new GitHubContentGetter( bookmarkGitLocation.repoUrl, bookmarkGitLocation.path, bookmarkGitLocation.branch, null );
        final String json = contentGetter.getContent();

        GsonBuilder builder = new GsonBuilder();

        final Map< String, String > bookmarkPathsToSha = new HashMap<>();
        ArrayList<LinkedTreeMap> linkedTreeMaps = ( ArrayList< LinkedTreeMap >) builder.create().fromJson( json, Object.class );
        for ( LinkedTreeMap linkedTreeMap : linkedTreeMaps )
        {
            final String downloadUrl = ( String ) linkedTreeMap.get( "download_url" );
            final String sha = (String) linkedTreeMap.get( "sha" );
            bookmarkPathsToSha.put( downloadUrl, sha );
        }
        return bookmarkPathsToSha;
    }

    private Map<String, String> getBookmarkFileNamesToPaths(Set<String> bookmarkPaths) {
        Map<String, String> bookmarkNamesToPaths = new HashMap<>();
        for (String path : bookmarkPaths) {
            bookmarkNamesToPaths.put(FileNameUtils.getBaseName(path), path);
        }
        return bookmarkNamesToPaths;
    }

    class FilePathAndSha {
        String filePath;
        String sha;
    }

    private FilePathAndSha getMatchingBookmarkFilePathAndSha () {
        Map<String, String> bookmarkPathsToSha = getFilePathsToSha();
        Map<String, String> bookmarkFileNamesToPaths = getBookmarkFileNamesToPaths(bookmarkPathsToSha.keySet());

        for (String bookmarkFileNameGithub : bookmarkFileNamesToPaths.keySet()) {
            if (bookmarkFileNameGithub.equals(bookmarkFileName)) {
                FilePathAndSha matchingFileAndSha = new FilePathAndSha();
                String matchingPath = bookmarkFileNamesToPaths.get(bookmarkFileNameGithub);
                matchingFileAndSha.filePath = matchingPath;
                matchingFileAndSha.sha = bookmarkPathsToSha.get(matchingPath);
                return matchingFileAndSha;
            }
        }

        return null;
    }

    public void writeBookmarksToGithub(ArrayList<Bookmark> bookmarks) {
        if (showDialog()) {
            try {
                HashMap<String, Bookmark> namesToBookmarks = new HashMap<>();
                for (Bookmark bookmark : bookmarks) {
                    namesToBookmarks.put(bookmark.name, bookmark);
                }

                // check for matching bookmark file on github
                FilePathAndSha matchingFilePathAndSha = getMatchingBookmarkFilePathAndSha();

                boolean appendToFile = false;
                if (matchingFilePathAndSha != null) {
                    appendToFile = bookmarksJsonParser.appendToFileDialog();
                }

                // don't continue if matching file was found, but user does not want to append to it
                if (!(matchingFilePathAndSha != null && !appendToFile)) {

                    Map<String, Bookmark> bookmarksInFile = new HashMap<>();

                    if (appendToFile) {
                        ArrayList<String> matchingFilePathsFromGithub = new ArrayList<>();
                        matchingFilePathsFromGithub.add(matchingFilePathAndSha.filePath);
                        Map<String, Bookmark> existingBookmarks = bookmarksJsonParser.parseBookmarks(matchingFilePathsFromGithub);
                        bookmarksInFile.putAll(existingBookmarks);
                    }
                    bookmarksInFile.putAll(namesToBookmarks);

                    final String bookmarkJsonBase64String = bookmarksJsonParser.writeBookmarksToBase64String(bookmarksInFile);

                    final GitHubFileCommitter fileCommitter;
                    if (appendToFile) {
                        fileCommitter = new GitHubFileCommitter(
                                bookmarkGitLocation.repoUrl, accessToken, bookmarkGitLocation.branch,
                                bookmarkGitLocation.path + "/" + bookmarkFileName + ".json", matchingFilePathAndSha.sha);
                    } else {
                        fileCommitter = new GitHubFileCommitter(
                                bookmarkGitLocation.repoUrl, accessToken, bookmarkGitLocation.branch,
                                bookmarkGitLocation.path + "/" + bookmarkFileName + ".json");
                    }
                    fileCommitter.commitStringAsFile("Add new bookmarks from UI", bookmarkJsonBase64String);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean showDialog()
    {
        final GenericDialog gd = new GenericDialog( "Save to github" );

        gd.addStringField( "GitHub access token", Prefs.get( ACCESS_TOKEN, "1234567890" ));
        gd.addStringField( "Bookmark file name", "");
        gd.showDialog();

        if ( gd.wasCanceled() ) return false;

        accessToken = gd.getNextString();
        bookmarkFileName = gd.getNextString();

        Prefs.set( ACCESS_TOKEN, accessToken );

        return true;
    }
}
