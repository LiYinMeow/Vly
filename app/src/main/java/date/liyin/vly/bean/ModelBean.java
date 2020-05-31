package date.liyin.vly.bean;

import android.view.View;
import android.widget.Button;

import date.liyin.vly.R;
import liyin.party.skyrecycleradapter.AutoDataBean;
import party.liyin.aralib.ARABind;
import party.liyin.aralib.ARABindLayout;

@ARABindLayout(R.layout.card_model)
public class ModelBean extends AutoDataBean {
    @ARABind(view_id = R.id.btn_model, view_method = "setText", view_type = Button.class)
    public String modelName;
    public long modelId;
    public String modelType;
    @ARABind(view_id = R.id.btn_model, view_method = "setOnClickListener", view_type = Button.class)
    public View.OnClickListener clickListener;
    @ARABind(view_id = R.id.btn_model, view_method = "setOnLongClickListener", view_type = Button.class)
    public View.OnLongClickListener longClickListener;

    public ModelBean(String modelName, long modelId, String modelType, View.OnClickListener clickListener, View.OnLongClickListener longClickListener) {
        this.modelName = modelName;
        this.modelId = modelId;
        this.modelType = modelType;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

}
