package com.wfs.storyboost;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.function.Predicate;

/** Staged edit dialog: only a successful Apply creates one undo step. */
final class ClipAdjustmentPanel {
    static void show(Activity activity,int index,ClipAdjustments prior,Predicate<ClipAdjustments> apply){
        LinearLayout form=StudioUi.card(activity,14);
        form.addView(StudioUi.text(activity,"WARNA & AUDIO  /  KLIP "+(index+1),12,StudioUi.CYAN,true));
        form.addView(StudioUi.text(activity,"Lihat hasil melalui Preview Timeline setelah Terapkan. Sumber asli tidak diubah.",12,StudioUi.MUTED,false));
        StudioUi.gap(form,activity,10);
        Spinner look=new Spinner(activity);
        look.setContentDescription("Color Look klip");
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,ColorLooks.names());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        look.setAdapter(adapter);look.setSelection(prior.look);form.addView(look);
        SeekBar brightness=slider(activity,form,"Brightness",prior.brightness);
        SeekBar contrast=slider(activity,form,"Contrast",prior.contrast);
        SeekBar saturation=slider(activity,form,"Saturation",prior.saturation);
        CheckBox mute=new CheckBox(activity);mute.setText("Bisukan audio klip");mute.setTextColor(StudioUi.TEXT);
        mute.setChecked(prior.mute);mute.setContentDescription("Bisukan audio klip");
        mute.setMinHeight(StudioUi.dp(activity,48));form.addView(mute);
        android.widget.Button reset=StudioUi.button(activity,"Reset penyesuaian",false);
        reset.setContentDescription("Reset penyesuaian klip");
        form.addView(reset,new LinearLayout.LayoutParams(-1,StudioUi.dp(activity,48)));
        reset.setOnClickListener(v->{look.setSelection(ColorLooks.ORIGINAL);brightness.setProgress(100);
            contrast.setProgress(100);saturation.setProgress(100);mute.setChecked(false);});
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);
        scroll.addView(form,new ScrollView.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Sesuaikan klip "+(index+1))
                .setView(scroll).setNegativeButton("Batal",null).setPositiveButton("Terapkan",null).show();
        // Keep native framework button IDs; text transformation varies by theme/API.
        android.widget.Button cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        android.widget.Button confirm=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        cancel.setAllCaps(false);confirm.setAllCaps(false);
        cancel.setContentDescription("Batalkan penyesuaian klip");
        confirm.setContentDescription("Terapkan penyesuaian klip");
        cancel.setMinHeight(StudioUi.dp(activity,48));confirm.setMinHeight(StudioUi.dp(activity,48));
        confirm.setOnClickListener(v->{
            ClipAdjustments next=new ClipAdjustments(look.getSelectedItemPosition(),brightness.getProgress()-100,
                    contrast.getProgress()-100,saturation.getProgress()-100,mute.isChecked());
            if(apply.test(next))dialog.dismiss();
        });
    }
    private static SeekBar slider(Activity a,LinearLayout form,String name,int value){
        StudioUi.gap(form,a,8);
        TextView label=StudioUi.text(a,name+"  "+value,13,StudioUi.TEXT,true);
        form.addView(label);
        SeekBar bar=new SeekBar(a);bar.setMax(200);bar.setProgress(value+100);
        bar.setKeyProgressIncrement(5);bar.setContentDescription(name+" klip");
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int p,boolean user){label.setText(name+"  "+(p-100));}
        });
        form.addView(bar,new LinearLayout.LayoutParams(-1,StudioUi.dp(a,48)));return bar;
    }
}
