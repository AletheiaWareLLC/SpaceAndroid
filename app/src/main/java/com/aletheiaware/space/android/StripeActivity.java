/*
 * Copyright 2018 Aletheia Ware LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aletheiaware.space.android;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.stripe.android.Stripe;
import com.stripe.android.TokenCallback;
import com.stripe.android.model.Card;
import com.stripe.android.model.Token;
import com.stripe.android.view.CardInputWidget;

public class StripeActivity extends AppCompatActivity {

    private EditText emailText;
    private CardInputWidget cardWidget;
    private CheckBox termsCheck;
    private CheckBox policyCheck;
    private CheckBox betaCheck;
    private FloatingActionButton stripeFab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup UI
        setContentView(R.layout.activity_stripe);
        TextView amountLabel = findViewById(R.id.stripe_amount_label);
        TextView amountText = findViewById(R.id.stripe_amount_text);
        emailText = findViewById(R.id.stripe_email_text);
        cardWidget = findViewById(R.id.stripe_card_widget);
        TextView legaleseLabel = findViewById(R.id.stripe_legalese_label);
        legaleseLabel.setMovementMethod(LinkMovementMethod.getInstance());
        termsCheck = findViewById(R.id.stripe_terms_of_service_check);
        policyCheck = findViewById(R.id.stripe_privacy_policy_check);
        TextView legaleseBetaLabel = findViewById(R.id.stripe_legalese_beta_label);
        legaleseBetaLabel.setMovementMethod(LinkMovementMethod.getInstance());
        betaCheck = findViewById(R.id.stripe_beta_test_agreement_check);
        stripeFab = findViewById(R.id.stripe_fab);
        stripeFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pay();
            }
        });
        String amount = getIntent().getStringExtra(SpaceAndroidUtils.STRIPE_AMOUNT_EXTRA);
        if (amount != null && !amount.isEmpty()) {
            amountLabel.setVisibility(View.VISIBLE);
            amountText.setVisibility(View.VISIBLE);
            amountText.setText(amount);
        } else {
            amountLabel.setVisibility(View.GONE);
            amountText.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        stripeFab.show();
    }

    public void pay() {
        // Email
        final String email = emailText.getText().toString();
        // TODO ensure email is valid
        if (email.isEmpty()) {
            SpaceAndroidUtils.showErrorDialog(StripeActivity.this, "Invalid email");
            return;
        }

        // Legal
        if (!termsCheck.isChecked()) {
            SpaceAndroidUtils.showErrorDialog(StripeActivity.this, getString(R.string.error_terms_of_service_required));
            return;
        }
        if (!policyCheck.isChecked()) {
            SpaceAndroidUtils.showErrorDialog(StripeActivity.this, getString(R.string.error_privacy_policy_required));
            return;
        }
        if (!betaCheck.isChecked()) {
            SpaceAndroidUtils.showErrorDialog(StripeActivity.this, getString(R.string.error_beta_test_agreement_required));
            return;
        }

        stripeFab.hide();

        // TODO mine terms of service agreement into blockchain
        // TODO mine privacy policy agreement into blockchain

        Card card = cardWidget.getCard();
        if (card != null) {
            card.setName(email);
            Stripe stripe = new Stripe(StripeActivity.this, getString(R.string.stripe_publishable_key));
            stripe.createToken(
                    card,
                    new TokenCallback() {
                        public void onSuccess(final Token token) {
                            Intent data = new Intent();
                            data.putExtra(SpaceAndroidUtils.EMAIL_EXTRA, email);
                            data.putExtra(SpaceAndroidUtils.STRIPE_TOKEN_EXTRA, token.getId());
                            setResult(RESULT_OK, data);
                            finish();
                        }

                        public void onError(Exception error) {
                            SpaceAndroidUtils.showErrorDialog(StripeActivity.this, R.string.error_stripe_invalid_payment, error);
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                    }
            );
        }
    }
}