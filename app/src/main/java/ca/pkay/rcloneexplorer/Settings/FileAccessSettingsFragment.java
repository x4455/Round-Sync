package ca.pkay.rcloneexplorer.Settings;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.Rclone;
import es.dmoral.toasty.Toasty;
import io.github.x0b.safdav.SafAccessProvider;
import io.github.x0b.safdav.file.SafConstants;

import java.util.ArrayList;
import java.util.List;

public class FileAccessSettingsFragment extends Fragment {

    public static final int onDocumentTreeOpened = 1001;

    private Context context;
    private ViewGroup fileAccessAll;
    private View safEnabledView;
    private Switch safEnabledSwitch;
    private PermissionListAdapter permissionList;
    private Button addPermissionBtn;
    private RecyclerView listView;
    private View addButtonContainer;
    private Rclone rclone;

    public static FileAccessSettingsFragment newInstance() {
        return new FileAccessSettingsFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_access_settings, container, false);

        permissionList = new PermissionListAdapter(context.getContentResolver().getPersistedUriPermissions());
        listView = view.findViewById(R.id.file_permission_list);
        listView.setAdapter(permissionList);
        addButtonContainer = view.findViewById(R.id.permission_add_container);

        getViews(view);
        setDefaultStates();
        setClickListeners();

        if (getActivity() != null) {
            getActivity().setTitle(getString(R.string.pref_header_file_access));
        }

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
        rclone = new Rclone(context);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.context = null;
    }

    private void getViews(View view) {
        safEnabledView = view.findViewById(R.id.enable_saf_view);
        safEnabledSwitch = view.findViewById(R.id.enable_saf_switch);
        fileAccessAll = view.findViewById(R.id.file_access_settings_all);
        addPermissionBtn = view.findViewById(R.id.permission_add_button);
    }

    private void setDefaultStates() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean safEnabled = sharedPreferences.getBoolean(getString(R.string.pref_key_enable_saf), false);
        safEnabledSwitch.setChecked(safEnabled);
    }

    private void setClickListeners() {
        safEnabledView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                safEnabledSwitch.setChecked(!safEnabledSwitch.isChecked());
            }
        });
        safEnabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setSafEnabled(isChecked);
            }
        });
        addPermissionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addRoot();
            }
        });
    }

    private void setSafEnabled(boolean isChecked) {
        if(isChecked) {
            listView.setVisibility(View.VISIBLE);
            addButtonContainer.setVisibility(View.VISIBLE);
            createSafRemote();
        } else {
            listView.setVisibility(View.GONE);
            addButtonContainer.setVisibility(View.GONE);
            rclone.deleteRemote(SafConstants.SAF_REMOTE_NAME);
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(getString(R.string.pref_key_enable_saf), isChecked);
        editor.apply();
    }

    private void createSafRemote() {
        String user = SafAccessProvider.getUser(getContext());
        String pass = SafAccessProvider.getPassword(getContext());
        ArrayList<String> options = new ArrayList<>();
        options.add(SafConstants.SAF_REMOTE_NAME);
        options.add("webdav");
        options.add("url");
        options.add(SafConstants.SAF_REMOTE_URL);
        options.add("user");
        options.add(user);
        options.add("pass");
        options.add(pass);

        Process process = rclone.configCreate(options);
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (process.exitValue() != 0) {
            Toasty.error(context, getString(R.string.error_creating_remote), Toast.LENGTH_SHORT, true).show();
        } else {
            Toasty.success(context, getString(R.string.remote_creation_success), Toast.LENGTH_SHORT, true).show();
        }
    }

    public void addRoot(){
        Intent treeOpenIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        treeOpenIntent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(treeOpenIntent, onDocumentTreeOpened);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(Activity.RESULT_OK == resultCode) {
            Uri uri = data.getData();
            if (null == uri || uri.getAuthority().equals("io.github.x0b.safdav")){
                Toasty.error(context, getString(R.string.saf_uri_permission_error), Toast.LENGTH_LONG, true).show();
                return;
            }
            if (requestCode == onDocumentTreeOpened) {
                final int takeFlags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                ContentResolver contentResolver = context.getContentResolver();
                for (UriPermission uriPermission : contentResolver.getPersistedUriPermissions()) {
                    if(uri.toString().startsWith(uriPermission.getUri().toString())){
                        // granted uri is less specific than already granted ones, reject
                        Toasty.error(context, getString(R.string.saf_uri_permission_already_added), Toast.LENGTH_LONG, true).show();
                        return;
                    }
                }
                // discard non-android storage devices
                if(!"com.android.externalstorage.documents".equals(uri.getAuthority())){
                    Toasty.error(context, getString(R.string.saf_uri_permission_no_support), Toast.LENGTH_LONG, true).show();
                    return;
                }
                contentResolver.takePersistableUriPermission(uri, takeFlags);
                permissionList.updatePermissions(context.getContentResolver().getPersistedUriPermissions());
                Toasty.normal(context, getString(R.string.saf_uri_permission_added), Toast.LENGTH_SHORT).show();
            }
        } else if(Activity.RESULT_CANCELED == resultCode){
            Toasty.normal(context, getString(R.string.saf_uri_permission_cancelled), Toast.LENGTH_SHORT).show();
        }
        super.onActivityResult(requestCode, resultCode, data);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private static class PermissionsViewHolder extends RecyclerView.ViewHolder {

        TextView text;
        UriPermission permission;

        public PermissionsViewHolder(@NonNull View itemView, final PermissionListAdapter adapter){
            super(itemView);
            text = itemView.findViewById(R.id.permission_path);
            itemView.findViewById(R.id.permission_remove).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    adapter.remove(getAdapterPosition());
                }
            });
        }
    }

    private class PermissionListAdapter extends RecyclerView.Adapter<PermissionsViewHolder> {

        List<UriPermission> permissions;

        public PermissionListAdapter(List<UriPermission> permissions) {
            this.permissions = permissions;
        }

        @NonNull
        @Override
        public PermissionsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int i) {
            View v = LayoutInflater.from(context).inflate(R.layout.fragment_permission_item, parent, false);
            return new PermissionsViewHolder(v, this);
        }

        @Override
        public void onBindViewHolder(@NonNull PermissionsViewHolder holder, int position) {
            holder.permission = permissions.get(position);
            String permissionLabel = holder.permission.getUri().getPath();
            if("/tree/primary:".equals(permissionLabel)) {
                permissionLabel = getString(R.string.pref_saf_permission_label_primary, permissionLabel);
            } else {
                permissionLabel = getString(R.string.pref_saf_permission_label_external, permissionLabel);
            }
            holder.text.setText(permissionLabel);
        }

        @Override
        public int getItemCount() {
            return permissions.size();
        }

        public void add(UriPermission permission){
            permissions.add(permission);
            notifyDataSetChanged();
        }

        public void remove(int index){
            UriPermission permission = permissions.get(index);
            context.getContentResolver().releasePersistableUriPermission(permission.getUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            permissions.remove(index);
            notifyDataSetChanged();
        }

        public void updatePermissions(List<UriPermission> newPermissions) {
            this.permissions = newPermissions;
            notifyDataSetChanged();
        }
    }
}
