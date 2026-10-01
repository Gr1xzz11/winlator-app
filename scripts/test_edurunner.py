#!/usr/bin/env python3
"""Host regressions for the real SAF copier and Shortcut parser.
Android provider/bitmap/JSON boundaries are fixtures; this does not run Wine.
Requires only Python 3 and JDK 17.
"""
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java'
STUBS = {
'android/content/Context.java': '''package android.content; public class Context { public String getPackageName(){return "test";} public Resources getResources(){return new Resources();} public String getString(int n){return "";} public static class Resources {public int getIdentifier(String a,String b,String c){return 0;}}}''',
'android/graphics/Bitmap.java': 'package android.graphics; public class Bitmap {}',
'android/graphics/BitmapFactory.java': 'package android.graphics; public class BitmapFactory {public static Bitmap decodeFile(String x){return null;}}',
'org/json/JSONException.java': 'package org.json; public class JSONException extends Exception {}',
'org/json/JSONObject.java': '''package org.json; public class JSONObject {private final java.util.Map<String,String> data=new java.util.LinkedHashMap<>(); public JSONObject put(String k,String v)throws JSONException{data.put(k,v);return this;} public boolean has(String k){return data.containsKey(k);} public String getString(String k)throws JSONException{return data.get(k);} public int length(){return data.size();} public java.util.Iterator<String> keys(){return data.keySet().iterator();} public void remove(String k){data.remove(k);}}''',
'com/winlator/container/Container.java': '''package com.winlator.container; public class Container {public java.io.File getIconsDir(int n){return new java.io.File("/missing");} public static void checkObsoleteOrMissingProperties(org.json.JSONObject d){}}''',
'com/winlator/core/FileUtils.java': '''package com.winlator.core; public class FileUtils {public static java.util.List<String> readLines(java.io.File f,boolean... trim){try{return java.nio.file.Files.readAllLines(f.toPath());}catch(Exception e){throw new RuntimeException(e);}} public static String getBasename(String p){return new java.io.File(p).getName().replaceFirst("\\\\.[^\\\\.]+$", "");} public static boolean writeString(java.io.File f,String s){try{java.nio.file.Files.writeString(f.toPath(),s);return true;}catch(Exception e){return false;}} public static void delete(java.io.File f){f.delete();}}''',
'com/winlator/core/WineUtils.java': 'package com.winlator.core; public class WineUtils {public static String dosToUnixPath(String s,com.winlator.container.Container c){return s;}}',
}

STUBS.update({
'android/net/Uri.java': 'package android.net; public class Uri {public final String id; public final boolean children; public Uri(String id,boolean children){this.id=id;this.children=children;} public static Uri parse(String s){return new Uri("root",false);} public String toString(){return "content://school/tree/root/document/"+id;}}',
'android/database/Cursor.java': 'package android.database; public interface Cursor extends AutoCloseable {boolean moveToFirst();boolean moveToNext();String getString(int i);void close();}',
'android/content/ContentResolver.java': 'package android.content; public abstract class ContentResolver {public abstract android.database.Cursor query(android.net.Uri u,String[] p,String s,String[] a,String o);public abstract java.io.InputStream openInputStream(android.net.Uri u)throws java.io.IOException;}',
'android/provider/DocumentsContract.java': 'package android.provider; public class DocumentsContract {public static String getTreeDocumentId(android.net.Uri u){return "root";}public static android.net.Uri buildDocumentUriUsingTree(android.net.Uri u,String id){return new android.net.Uri(id,false);}public static android.net.Uri buildChildDocumentsUriUsingTree(android.net.Uri u,String id){return new android.net.Uri(id,true);}public static class Document{public static final String COLUMN_DOCUMENT_ID="id",COLUMN_DISPLAY_NAME="name",COLUMN_MIME_TYPE="mime",MIME_TYPE_DIR="directory";}}',
})
IMPORT_TEST = r''' 
static class Rows implements android.database.Cursor {
final String[][] rows;int index=-1; Rows(String[][] rows){this.rows=rows;}
public boolean moveToFirst(){index=0;return rows.length>0;}
public boolean moveToNext(){return ++index<rows.length;}
public String getString(int i){return rows[index][i];} public void close(){}
}
static class Provider extends android.content.ContentResolver {
byte[] large=new byte[150001]; boolean deny;
Provider(){for(int i=0;i<large.length;i++)large[i]=(byte)(i%251);}
public android.database.Cursor query(android.net.Uri uri,String[] p,String s,String[] a,String o){
if(!uri.children)return new Rows(new String[][]{{"Big English","directory"}});
if(uri.id.equals("root"))return new Rows(new String[][]{
{"lessons","уроки","directory"},{"dll","aé.dll","file"},{"empty","Empty","directory"},{"zero","empty.txt","file"}});
if(uri.id.equals("lessons"))return new Rows(new String[][]{{"exe","Start App.EXE","file"},{"setup","Setup.exe","file"}});
return new Rows(new String[][]{});
}
public java.io.InputStream openInputStream(android.net.Uri uri)throws IOException{
if(deny)throw new IOException("provider denied");
return new java.io.ByteArrayInputStream(uri.id.equals("zero")?new byte[0]:large);
}}
static void importerTests()throws Exception {
Provider provider=new Provider(); Path temp=Files.createTempDirectory("saf-import");
EduRunnerFolderImporter.Result result=new EduRunnerFolderImporter(provider).copy(android.net.Uri.parse("content://school/tree/root"),temp.toFile());
if(result.executables.size()!=2)throw new AssertionError("Recursive case-insensitive EXE discovery failed");
if(!Files.isDirectory(temp.resolve("Empty")))throw new AssertionError("Empty directory lost");
if(Files.size(temp.resolve("empty.txt"))!=0)throw new AssertionError("Empty file lost");
if(!java.util.Arrays.equals(provider.large,Files.readAllBytes(temp.resolve("aé.dll"))))throw new AssertionError("DLL bytes changed");
if(!Files.isRegularFile(temp.resolve("уроки/Start App.EXE")))throw new AssertionError("File name/structure changed");
String rel=EduRunnerProgramFiles.relative(temp.toFile(),result.executables.stream().filter(f->f.getName().equals("Start App.EXE")).findFirst().get());
if(!rel.equals("уроки/Start App.EXE"))throw new AssertionError("Relative EXE path wrong");
boolean rejected=false;try{EduRunnerProgramFiles.checkedChild(temp.toFile(),"../escape");}catch(IOException expected){rejected=true;}
if(!rejected)throw new AssertionError("Provider path traversal accepted");
provider.deny=true;rejected=false;
try{new EduRunnerFolderImporter(provider).copy(android.net.Uri.parse("content://school/tree/root"),Files.createTempDirectory("saf-denied").toFile());}catch(IOException expected){rejected=true;}
if(!rejected)throw new AssertionError("Incomplete import silently accepted");
System.out.println("PASS: SAF recursion, exact bytes/names, empty entries, multiple EXEs, denied reads and unsafe paths");
}
'''

with tempfile.TemporaryDirectory() as tmp:
    tmp = pathlib.Path(tmp)
    for path, content in STUBS.items():
        f = tmp / path
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(content)
    fragment = (SOURCE / 'com/winlator/EduRunnerHomeFragment.java').read_text()
    helper = SOURCE / 'com/winlator/EduRunnerProgramFiles.java'
    if helper.exists():
        generation = '''String winPath=EduRunnerProgramFiles.windowsPath("Big English",rel);
        String desktopText=EduRunnerProgramFiles.desktopEntry(name,winPath);'''
    else:
        # Exercise the exact existing generation code, not a reimplementation of it.
        generation = '\n'.join(line.strip() for line in fragment.splitlines()
                               if 'String winPath=' in line or 'String desktopText=' in line)
    main = '''package com.winlator;
import java.io.*; import java.nio.file.*;
import com.winlator.container.Shortcut; import com.winlator.container.Container;
public class Regression {
static String safeName(String s){return s.replaceAll("[^a-zA-Z0-9._ -]","_").trim();}
public static void main(String[] args)throws Exception{
String name="Big English",rel="уроки/Start App.EXE";
GENERATION
Path directory=Files.createTempDirectory("edurunner-shortcut");
File shortcutFile=directory.resolve("Big English.desktop").toFile();
Files.writeString(shortcutFile.toPath(),desktopText);
Shortcut shortcut=new Shortcut(new Container(),shortcutFile);
String expected="C:\\GRXT\\Big English\\уроки\\Start App.EXE";
if(!expected.equals(shortcut.path))throw new AssertionError("Shortcut path: expected ["+expected+"] got ["+shortcut.path+"]");
if(shortcut.isLinkPath())throw new AssertionError("Local EXE classified as a URL/link");
System.out.println("PASS: real Shortcut parser resolves nested EXE, spaces and Cyrillic without quotes");
}}
'''.replace('GENERATION', generation)
    # Java DOS string constants require two backslashes in source.
    main = main.replace('String expected="C:\\GRXT\\Big English\\уроки\\Start App.EXE";',
                        r'String expected="C:\\GRXT\\Big English\\уроки\\Start App.EXE";')
    test = tmp / 'com/winlator/Regression.java'
    test.parent.mkdir(parents=True, exist_ok=True)
    if (SOURCE / 'com/winlator/EduRunnerFolderImporter.java').exists():
        main = main.replace('public static void main(String[] args)', IMPORT_TEST + '\npublic static void main(String[] args)')
        main = main.replace('String name="Big English",rel=', 'importerTests();\nString name="Big English",rel=')
    test.write_text(main)
    files = list(tmp.rglob('*.java')) + [SOURCE / 'com/winlator/core/StringUtils.java', SOURCE / 'com/winlator/container/Shortcut.java']
    if helper.exists():
        files.append(helper)
    importer = SOURCE / 'com/winlator/EduRunnerFolderImporter.java'
    if importer.exists():
        files.append(importer)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(tmp / 'classes'), *map(str, files)], check=True)
    subprocess.run(['java', '-cp', str(tmp / 'classes'), 'com.winlator.Regression'], check=True)
