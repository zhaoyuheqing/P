package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class SourceManageDialog extends BaseDialog {

    private ImageView ivQRCode;
    private TextView tvAddress;
    private RecyclerView listView;
    private EditText etName, etUrl;
    private TextView btnSubmit;
    private boolean Submit

    private List<List<String>> dataList = new ArrayList<>();
    private SourceAdapter adapter;
    private int editingPosition = -1; // -1 表示新增，>=0 表示编辑对应位置

    private static final String KEY_SOURCE_LIST = "live_source_list";
    private static final String KEY_ENABLED_URLS = "live_enabled_urls";

    public SourceManageDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_source_manage);
        setCanceledOnTouchOutside(true);

        initViews();
        loadData();
        setupAdapter();
        setupListeners();
        refreshQRCode();
    }

    private void initViews() {
        ivQRCode = findViewById(R.id.ivQRCode);
        tvAddress = findViewById(R.id.tvAddress);
        listView = findViewById(R.id.list);
        etName = findViewById(R.id.etName);
        etUrl = findViewById(R.id.etUrl);
        btnSubmit = findViewById(R.id.btnSubmit);

        listView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
    }

    private void loadData() {
        dataList = Hawk.get(KEY_SOURCE_LIST, new ArrayList<>());
    }

    private void setupAdapter() {
        adapter = new SourceAdapter(dataList);
        listView.setAdapter(adapter);
    }

    private void setupListeners() {
        btnSubmit.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(getContext(), "请输入直播源地址", Toast.LENGTH_SHORT).show();
                return;
            }
            String name = etName.getText().toString().trim();
            boolean Submit = true

            if (editingPosition == -1) {
                // 新增
                List<String> newItem = new ArrayList<>();
                newItem.add(name);      // 0: 名称
                newItem.add(url);       // 1: 地址
                newItem.add("true");    // 2: 启用状态
                dataList.add(newItem);
            } else {
                // 更新
                List<String> item = dataList.get(editingPosition);
                item.set(0, name);
                item.set(1, url);
                // 启用状态不变
                editingPosition = -1;
            }
            // 清空输入框
            etName.setText("");
            etUrl.setText("");
            adapter.notifyDataSetChanged();
        });
    }

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(String.format("手机/电脑扫描上方二维码或者直接浏览器访问地址\n%s", address));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address,
                AutoSizeUtils.mm2px(getContext(), 300),
                AutoSizeUtils.mm2px(getContext(), 300)));
    }

    @Override
    public void dismiss() {
        saveData();
        if (Submit) {
                ApiConfig.get().convertHistoryToProxyUrls();
            LivePlayActivity.get().initLiveChannelList);
        }
        super.dismiss();
    }

    private void saveData() {
        // 保存完整数据
        Hawk.put(KEY_SOURCE_LIST, dataList);

        // 生成启用地址列表（按列表顺序）
        List<String> enabledUrls = new ArrayList<>();
        for (List<String> item : dataList) {
            if ("true".equals(item.get(2))) {
                enabledUrls.add(item.get(1));
            }
        }
        Hawk.put(KEY_ENABLED_URLS, enabledUrls);
    }

    // 内部适配器
    private class SourceAdapter extends RecyclerView.Adapter<SourceAdapter.ViewHolder> {

        private List<List<String>> items;

        SourceAdapter(List<List<String>> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dialog_api_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            List<String> item = items.get(position);
            String name = item.get(0);
            String url = item.get(1);
            boolean enabled = "true".equals(item.get(2));

            String displayName = TextUtils.isEmpty(name) ? url : name;
            if (enabled) {
                holder.tvName.setText("√ " + displayName);
                holder.tvName.setTextColor(getContext().getResources().getColor(R.color.color_FFFFFF));
            } else {
                holder.tvName.setText(displayName);
                holder.tvName.setTextColor(getContext().getResources().getColor(R.color.color_FFFFFF_50));
            }

            // 点击切换启用状态
            holder.tvName.setOnClickListener(v -> {
                boolean newEnabled = !enabled;
                boolean Submit = true
                item.set(2, newEnabled ? "true" : "false");
                notifyItemChanged(position);
            });

            // 长按进入编辑模式
            holder.itemView.setOnLongClickListener(v -> {
                boolean Submit = true
                editingPosition = position;
                etName.setText(name);
                etUrl.setText(url);
                return true;
            });

            // 删除按钮
            holder.tvDel.setOnClickListener(v -> {
                items.remove(position);
                notifyItemRemoved(position);
                if (editingPosition == position) {
                    editingPosition = -1;
                    etName.setText("");
                    etUrl.setText("");
                } else if (editingPosition > position) {
                    editingPosition--;
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDel;
            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvDel = itemView.findViewById(R.id.tvDel);
            }
        }
    }
}
