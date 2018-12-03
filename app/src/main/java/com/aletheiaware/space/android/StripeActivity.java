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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.stripe.android.Stripe;
import com.stripe.android.TokenCallback;
import com.stripe.android.model.Card;
import com.stripe.android.model.Token;
import com.stripe.android.view.CardInputWidget;

import java.io.PrintWriter;
import java.io.StringWriter;

public class StripeActivity extends AppCompatActivity {

    private TextView amountText;
    private EditText emailText;
    private CardInputWidget cardWidget;
    private FloatingActionButton payFab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stripe);
        String amount = getIntent().getStringExtra(SpaceAndroidUtils.STRIPE_AMOUNT_EXTRA);
        if (amount != null && !amount.isEmpty()) {
            amountText = findViewById(R.id.stripe_amount_text);
            amountText.setVisibility(View.VISIBLE);
            amountText.setText(amount);
        }
        emailText = findViewById(R.id.stripe_email_text);
        cardWidget = findViewById(R.id.stripe_card_widget);
        payFab = findViewById(R.id.stripe_fab);
        payFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pay();
            }
        });
    }

    public void pay() {
        final String email = emailText.getText().toString();
        Card card = cardWidget.getCard();
        if (card != null) {
            card.setName(email);
            Stripe stripe = new Stripe(StripeActivity.this, "pk_test_gvdQJ2CsMiwE0gARM6nGUbUb");
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
                            error.printStackTrace();
                            StringWriter sw = new StringWriter();
                            error.printStackTrace(new PrintWriter(sw));
                            Toast.makeText(StripeActivity.this, sw.toString(), Toast.LENGTH_LONG).show();
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                    }
            );
        }
    }
}