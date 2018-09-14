package org.elbe.relations.mobile.preferences

import android.content.res.Resources
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.preference.PreferenceDialogFragmentCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.elbe.relations.mobile.R

/**
 * The dialog to edit the CloudConfigPreference.
 *
 * @see https://medium.com/@JakobUlbrich/building-a-settings-screen-for-android-part-3-ae9793fd31ec
 */
class CloudConfigPreferenceDialogFragmentCompat(): PreferenceDialogFragmentCompat() {
    private lateinit var mCloudProviderView: RCWrapper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater!!.inflate(R.layout.pref_dialog_cloud_config, container, false)
    }

    override fun onBindDialogView(view: View?) {
        super.onBindDialogView(view)

        mCloudProviderView = RCWrapper(view?.findViewById<RecyclerView>(R.id.cloud_config_entries))
        mCloudProviderView.apply {
            setHasFixedSize(true)
            setLayoutManager(LinearLayoutManager(activity, LinearLayout.VERTICAL, false))
            setAdapter(ProviderAdapter(resources))
        }

        // set cloud provider id to dialog
        var cloudProviderId = ""
        val pref = preference
        if (pref is CloudConfigPreference) {
            // set preference to list
            cloudProviderId = pref.getCloudConfig()
        }
        if (!cloudProviderId.isEmpty()) {
            mCloudProviderView.setCloudProviderId(cloudProviderId)
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            // generate value to save
            val cloudProviderId = mCloudProviderView.getCloudProviderId()
            // mRecyclerView get value
            val pref = preference
            if (pref is CloudConfigPreference) {
                // save the value
                pref.setCloudConfig(cloudProviderId)
            }
        }
    }

// ---

    private class RCWrapper(recyclerView: RecyclerView?) {
        val mRecyclerView = recyclerView

        fun setHasFixedSize(fixedSize: Boolean) {
            mRecyclerView?.setHasFixedSize(fixedSize)
        }

        fun setLayoutManager(layout: RecyclerView.LayoutManager) {
            mRecyclerView?.layoutManager = layout
        }

        fun setAdapter(adapter: ProviderAdapter) {
            mRecyclerView?.adapter = adapter
        }

        fun setCloudProviderId(cloudProviderId: String) {
            val adapter = mRecyclerView?.adapter
            if (adapter is ProviderAdapter) {
                adapter.setActiveProviderId(cloudProviderId)
            }
        }

        fun getCloudProviderId(): String {
            val adapter = mRecyclerView?.adapter
            if (adapter is ProviderAdapter) {
                return adapter.getActiveProviderId()
            }
            return ""
        }

    }

    private class ProviderAdapter(resources: Resources): RecyclerView.Adapter<ProviderAdapter.ViewHolder>() {
        val mProviders = CloudProviders(resources).getProviders()
        val mRadioGroup = mutableListOf<RadioButton>()
        val mMapping = HashMap<String, RadioButton>(mProviders.size)
        var mProviderId = ""

        fun setActiveProviderId(id: String) {
            mProviderId = id
        }

        fun getActiveProviderId(): String {
            for ((id, radio) in mMapping) {
                if (radio.isChecked) {
                    return id
                }
            }
            return ""
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.let {holder ->
                holder.providerName.text = mProviders[position].name
                val hint = mProviders[position].hint
                holder.providerToken.hint = hint
                if (hint.isEmpty()) {
                    holder.providerToken.visibility = View.GONE
                }
                val id = mProviders[position].id
                val radio = holder.setId(id, hint.isEmpty())
                mMapping.put(id, radio)
                if (id.equals(mProviderId)) {
                    radio.isChecked = true
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProviderAdapter.ViewHolder {
            val v = LayoutInflater.from(parent?.context).inflate(R.layout.cloud_provider, parent, false)
            return ViewHolder(v, mRadioGroup)
        }

        override fun getItemCount(): Int {
            return mProviders.size
        }

        class ViewHolder(view: View, radioGroup: MutableList<RadioButton>): RecyclerView.ViewHolder(view) {
            var mId: String = ""
            val mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(view.context)
            val mRadioGroup = radioGroup
            val providerName: TextView = view.findViewById<TextView>(R.id.cloud_provider_name)
            val providerToken: EditText = view.findViewById<EditText>(R.id.cloud_provider_token)
            val activRadio: RadioButton = view.findViewById<RadioButton>(R.id.cloud_provider_id)
            val editor = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = Unit
                override fun beforeTextChanged(s: CharSequence, p1: Int, p2: Int, p3: Int) = Unit
                override fun onTextChanged(s: CharSequence, p1: Int, p2: Int, p3: Int) {
                    if (s.isEmpty()) {
                        activRadio.isEnabled = false
                        activRadio.isChecked = false
                    } else {
                        activRadio.isEnabled = true
                    }
                }
            }

            init {
                mRadioGroup.add(activRadio)
                // listeners
                providerToken.addTextChangedListener(editor)
                providerToken.setOnFocusChangeListener { view, focus -> handleBlur(view, focus) }
                activRadio.setOnClickListener { handleClick() }
            }

            fun setId(id: String, alwaysEnabled: Boolean): RadioButton {
                mId = id
                providerToken.setText(mSharedPrefs.getString(mId, ""))
                if (alwaysEnabled) {
                    activRadio.isEnabled = true
                } else if (providerToken.text.isEmpty()) {
                    activRadio.isEnabled = false
                }
                return activRadio
            }

            private fun handleClick() {
                storeTokenValue(providerToken.text.toString())
                mRadioGroup.forEach {radio ->
                    if (radio != activRadio) {
                        radio.isChecked = false
                    }
                }
            }

            private fun handleBlur(v: View, hasFocus: Boolean) {
                if (!hasFocus) {
                    // store token value
                    if (v is EditText) {
                        storeTokenValue(v.text.toString())
                    }
                }
            }

            private fun storeTokenValue(token: String) {
                with (mSharedPrefs.edit()) {
                    putString(mId, token)
                    commit()
                }
            }

        }
    }

//    ---

    companion object {

        fun newInstance(key: String): CloudConfigPreferenceDialogFragmentCompat {
            val fragment = CloudConfigPreferenceDialogFragmentCompat()
            val args = Bundle()
            args.putString(ARG_KEY, key)
            fragment.arguments = args
            return  fragment
        }
    }

}