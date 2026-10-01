package com.winlator;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;
import com.winlator.core.FileUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.Executors;

public class EduRunnerHomeFragment extends Fragment {
    private static final String DEV_PIN = "1283256";
    private RecyclerView recyclerView;
    private View emptyView;
    private ContainerManager manager;
    private int versionTaps;
    private long lastVersionTap;

    private final ActivityResultLauncher<Uri> folderPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return;
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) {}
                importFolder(uri);
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.edurunner_home_fragment, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        manager = new ContainerManager(requireContext());
        recyclerView = view.findViewById(R.id.ProgramGrid);
        emptyView = view.findViewById(R.id.EmptyPrograms);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(),
                getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 4 : 3));
        view.findViewById(R.id.SettingsButton).setOnClickListener(v -> showSettings());
        refresh();
    }

    private void showSettings() {
        String[] items = {"О приложении"};
        new AlertDialog.Builder(requireContext()).setTitle("Настройки")
                .setItems(items, (d, which) -> showAbout()).setNegativeButton("Закрыть", null).show();
    }

    private void showAbout() {
        TextView version = new TextView(requireContext());
        version.setText("GRXT EduRunner\n\nВерсия 0.1.0-winlator11.2");
        version.setTextColor(0xFFFFFFFF); version.setTextSize(18);
        version.setGravity(android.view.Gravity.CENTER);
        int pad=(int)(28*getResources().getDisplayMetrics().density);
        version.setPadding(pad,pad,pad,pad);
        version.setOnClickListener(v -> onVersionTap());
        new AlertDialog.Builder(requireContext()).setTitle("О приложении").setView(version)
                .setPositiveButton("Готово", null).show();
    }

    private void onVersionTap() {
        long now=System.currentTimeMillis();
        if(now-lastVersionTap>2500) versionTaps=0;
        lastVersionTap=now;
        if(++versionTaps>=7){ versionTaps=0; showDeveloperPin(); }
    }

    private void showDeveloperPin() {
        EditText input=new EditText(requireContext());
        input.setHint("PIN");
        input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        AlertDialog dialog=new AlertDialog.Builder(requireContext()).setTitle("Developer Mode")
                .setMessage("Введите PIN разработчика").setView(input).setNegativeButton("Отмена",null)
                .setPositiveButton("Войти",null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if(DEV_PIN.equals(input.getText().toString())){ dialog.dismiss(); showDeveloperPanel(); }
            else input.setError("Неверный PIN");
        }));
        dialog.show();
    }

    private void showDeveloperPanel() {
        String[] items={"Добавить программу","Контейнеры и Runtime","Экран и графика","Совместимость","Отладка","Расширенные настройки"};
        new AlertDialog.Builder(requireContext()).setTitle("Developer Mode").setItems(items,(d,which)->{
            if(which==0) folderPicker.launch(null);
            else Toast.makeText(requireContext(),items[which]+" — в разработке",Toast.LENGTH_SHORT).show();
        }).setNegativeButton("Закрыть",null).show();
    }

    private void importFolder(Uri treeUri) {
        Toast.makeText(requireContext(),"Импорт папки…",Toast.LENGTH_LONG).show();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String treeId=DocumentsContract.getTreeDocumentId(treeUri);
                Uri root=DocumentsContract.buildDocumentUriUsingTree(treeUri,treeId);
                String name=queryName(root);
                if(name==null||name.trim().isEmpty()) name="Program";
                File base=new File(requireContext().getFilesDir(),"edurunner-imports");
                File dst=new File(base,safeName(name)+"-"+System.currentTimeMillis());
                dst.mkdirs();
                ArrayList<File> exes=new ArrayList<>();
                copyDocumentTree(root,dst,exes);
                final String programName=name;
                requireActivity().runOnUiThread(() -> {
                    if(exes.isEmpty()) {
                        Toast.makeText(requireContext(),"В папке не найдено .exe",Toast.LENGTH_LONG).show();
                    } else if(exes.size()==1) {
                        createProgram(programName,dst,exes.get(0));
                    } else {
                        String[] names=new String[exes.size()];
                        for(int i=0;i<exes.size();i++) names[i]=relative(dst,exes.get(i));
                        new AlertDialog.Builder(requireContext()).setTitle("Какой EXE запускать?")
                                .setItems(names,(d,which)->createProgram(programName,dst,exes.get(which))).show();
                    }
                });
            } catch(Exception e) {
                requireActivity().runOnUiThread(() -> new AlertDialog.Builder(requireContext())
                        .setTitle("Ошибка импорта").setMessage(e.toString()).setPositiveButton("OK",null).show());
            }
        });
    }

    private void createProgram(String name, File importedRoot, File exe) {
        Toast.makeText(requireContext(),"Создаю среду запуска…",Toast.LENGTH_LONG).show();
        try {
            JSONObject data=new JSONObject();
            data.put("name",name);
            data.put("screenSize","1280x720");
            data.put("dxwrapper","wined3d");
            manager.createContainerAsync(data, container -> {
                if(container==null){ Toast.makeText(requireContext(),"Не удалось создать контейнер",Toast.LENGTH_LONG).show(); return; }
                try {
                    File programDir=new File(container.getRootDir(),".wine/drive_c/GRXT/"+safeName(name));
                    programDir.getParentFile().mkdirs();
                    if(!importedRoot.renameTo(programDir)) {
                        if(!FileUtils.copy(importedRoot,programDir,null)) throw new Exception("Не удалось скопировать программу в контейнер");
                        FileUtils.delete(importedRoot);
                    }
                    String rel=relative(importedRoot,exe);
                    File targetExe=new File(programDir,rel);
                    File desktop=new File(container.getUserDir(),"Desktop");
                    desktop.mkdirs();
                    File shortcut=new File(desktop,safeName(name)+".desktop");
                    String winPath="C:\\\\GRXT\\\\"+safeName(name)+"\\\\"+rel.replace("/","\\\\");
                    String desktopText="[Desktop Entry]\nName="+name+"\nExec=wine \\\""+winPath+"\\\"\nType=Application\n";
                    FileUtils.writeString(shortcut,desktopText);
                    refresh();
                    Toast.makeText(requireContext(),"Готово: "+name,Toast.LENGTH_LONG).show();
                } catch(Exception e) {
                    new AlertDialog.Builder(requireContext()).setTitle("Ошибка").setMessage(e.toString()).setPositiveButton("OK",null).show();
                }
            });
        } catch(Exception e) {
            new AlertDialog.Builder(requireContext()).setTitle("Ошибка").setMessage(e.toString()).setPositiveButton("OK",null).show();
        }
    }

    private void copyDocumentTree(Uri doc, File dst, ArrayList<File> exes) throws Exception {
        if(isDirectory(doc)) {
            dst.mkdirs();
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(doc,DocumentsContract.getDocumentId(doc));
            try(Cursor c=requireContext().getContentResolver().query(children,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
                if(c!=null) while(c.moveToNext()){
                    String id=c.getString(0), n=c.getString(1);
                    Uri child=DocumentsContract.buildDocumentUriUsingTree(doc,id);
                    copyDocumentTree(child,new File(dst,safeNameKeepExt(n)),exes);
                }
            }
        } else {
            dst.getParentFile().mkdirs();
            try(InputStream in=requireContext().getContentResolver().openInputStream(doc);
                FileOutputStream out=new FileOutputStream(dst)){
                if(in==null) throw new Exception("Не удалось открыть "+doc);
                byte[] buf=new byte[65536]; int n;
                while((n=in.read(buf))>0) out.write(buf,0,n);
            }
            if(dst.getName().toLowerCase().endsWith(".exe")) exes.add(dst);
        }
    }

    private boolean isDirectory(Uri uri) {
        try(Cursor c=requireContext().getContentResolver().query(uri,new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
            return c!=null&&c.moveToFirst()&&DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(0));
        }
    }
    private String queryName(Uri uri) {
        try(Cursor c=requireContext().getContentResolver().query(uri,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            return c!=null&&c.moveToFirst()?c.getString(0):null;
        }
    }
    private static String safeName(String s){ return s.replaceAll("[^a-zA-Z0-9._ -]","_").trim(); }
    private static String safeNameKeepExt(String s){ String x=s.replaceAll("[\\\\/:*?\\\"<>|]","_"); return x.isEmpty()?"file":x; }
    private static String relative(File root,File file){
        String rp=root.getAbsolutePath(), fp=file.getAbsolutePath();
        return fp.startsWith(rp)?fp.substring(rp.length()).replaceFirst("^/",""):file.getName();
    }

    @Override public void onResume(){ super.onResume(); if(manager!=null) refresh(); }
    private void refresh(){
        ArrayList<Shortcut> items=manager.loadShortcuts(null);
        items.removeIf(s->s.file.isDirectory());
        emptyView.setVisibility(items.isEmpty()?View.VISIBLE:View.GONE);
        recyclerView.setVisibility(items.isEmpty()?View.GONE:View.VISIBLE);
        recyclerView.setAdapter(new Adapter(items));
    }
    private void run(Shortcut s){
        Intent i=new Intent(requireContext(),XServerDisplayActivity.class);
        i.putExtra("container_id",s.container.id); i.putExtra("shortcut_path",s.file.getPath()); startActivity(i);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder>{
        private final ArrayList<Shortcut> items; Adapter(ArrayList<Shortcut> i){items=i;}
        class Holder extends RecyclerView.ViewHolder{
            android.widget.ImageView icon; TextView title,subtitle;
            Holder(View v){super(v);icon=v.findViewById(R.id.ProgramIcon);title=v.findViewById(R.id.ProgramTitle);subtitle=v.findViewById(R.id.ProgramSubtitle);}
        }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p,int t){return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.edurunner_program_card,p,false));}
        @Override public void onBindViewHolder(@NonNull Holder h,int p){Shortcut s=items.get(p);if(s.icon!=null)h.icon.setImageBitmap(s.icon);else h.icon.setImageResource(R.mipmap.ic_launcher);h.title.setText(s.name);h.subtitle.setText("Запустить");h.itemView.setOnClickListener(v->run(s));}
        @Override public int getItemCount(){return items.size();}
    }
}
