package com.example.uia93237.chatbot;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;


import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import ai.api.AIDataService;
import ai.api.AIListener;
import ai.api.AIServiceException;
import ai.api.android.AIConfiguration;
import ai.api.android.AIService;
import ai.api.model.AIContext;
import ai.api.model.AIError;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Result;


public class MainActivity extends AppCompatActivity implements AIListener {

    private final static String accessToken = "aa61d172c55d430c8c958e2312e1658c";
    private final static String botName = "Anastasia";
    private final static String userName = "User";
    private final static int PERMISSION_CODE = 1;

    private EditText editText;

    private TextToSpeech tts;

    // Firebase DB
    private DatabaseReference ref;

    // Firebase Recycler Adapter
    private FirebaseRecyclerAdapter<Message, ChatViewHolder> adapter;

    // Dialogflow service
    private AIService aiService;
    private AIDataService aiDataService;


    // boolean used to switch between the 2 pictograms in the fab button
    boolean flagFab = true;


    @Override
    protected void onCreate(Bundle savedInstanceState){

        super.onCreate(savedInstanceState);

        // Request Audio Permission
        ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO }, PERMISSION_CODE);

        // Set up view
        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        editText = findViewById(R.id.editText);
        RelativeLayout addBtn = findViewById(R.id.addBtn);

        recyclerView.setHasFixedSize(true);
        final LinearLayoutManager linearLayoutManager=new LinearLayoutManager(this);
        linearLayoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(linearLayoutManager);

        // Set up TTS
        tts = new TextToSpeech(getApplicationContext(), (status) -> {
            if(status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.UK);
            }
        });


        // Set up Firebase
        ref = FirebaseDatabase.getInstance().getReference();
        ref.keepSynced(true);


        // Set up Dialogflow
        final AIConfiguration config = new AIConfiguration(accessToken, AIConfiguration.SupportedLanguages.English,
                AIConfiguration.RecognitionEngine.System);

        aiService = AIService.getService(this, config);
        aiService.setListener(this);

        aiDataService = new AIDataService(config);

        // Inititialize request to DialogFlow
        final AIRequest aiRequest = new AIRequest();
        // Set location related context
        FusedLocationProviderClient mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        try {
            mFusedLocationClient.getLastLocation().addOnSuccessListener((location) -> {
                if (location != null) {
                    AIContext currentLoc = new AIContext("weather");
                    Map<String, String> locMap = new HashMap<>();
                    locMap.put("latitude", String.valueOf(location.getLatitude()));
                    locMap.put("longitude", String.valueOf(location.getLongitude()));
                    currentLoc.setParameters(locMap);
                    aiRequest.setContexts(Collections.singletonList(currentLoc));
                }
            });
        } catch(SecurityException e) {
            Log.d("EXCEPTION: ", e.getMessage());
        }

        // Set up send message button listener
        addBtn.setOnClickListener(view -> {

            view.playSoundEffect(android.view.SoundEffectConstants.CLICK);

            String message = editText.getText().toString().trim();

            if (!message.equals("")) {

                Message chatMessage = new Message(message, userName);
                ref.child("chat").push().setValue(chatMessage);

                aiRequest.setQuery(message);

                new QueryTask().execute(aiRequest);

            }
            else { // the else block takes care of voice input
                aiService.startListening();
            }

            editText.setText("");
        });


        // Configure listener for edit text in order to change the pic
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                ImageView fab_img = findViewById(R.id.fab_img);
                Bitmap img = BitmapFactory.decodeResource(getResources(), R.drawable.ic_send_white_24dp);
                Bitmap img1 = BitmapFactory.decodeResource(getResources(), R.drawable.ic_mic_white_24dp);

                if (s.toString().trim().length() != 0 && flagFab) {
                    imageViewAnimatedChange(MainActivity.this, fab_img, img);
                    flagFab = false;
                } else if (s.toString().trim().length() == 0) {
                    imageViewAnimatedChange(MainActivity.this, fab_img, img1);
                    flagFab = true;
                }
            }

            @Override public void afterTextChanged(Editable s) {}
        });


        /*
         Configure FirebaseRecyclerAdapter -> FirebaseUI
          */

        // Create Adapter
        FirebaseRecyclerOptions<Message> options =
                new FirebaseRecyclerOptions.Builder<Message>()
                        .setQuery(ref.child("chat"), Message.class)
                        .build();

        adapter = new FirebaseRecyclerAdapter<Message, ChatViewHolder>(options){

            @Override
            protected void onBindViewHolder(@NonNull ChatViewHolder holder, int position, @NonNull Message model) {
                if(model.getMsgUser().equals(userName)){


                    holder.rightText.setText(model.getMsgText());

                    holder.rightText.setVisibility(View.VISIBLE);
                    holder.leftText.setVisibility(View.GONE);
                }
                else{
                    holder.leftText.setText(model.getMsgText());

                    holder.rightText.setVisibility(View.GONE);
                    holder.leftText.setVisibility(View.VISIBLE);
                }
            }

            @NonNull
            @Override
            public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.msglist, parent, false);

                return new ChatViewHolder(view);

            }
        };


        // Register the Adapter
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);

                int msgCount = adapter.getItemCount();
                int lastVisiblePosition = linearLayoutManager.findLastCompletelyVisibleItemPosition();

                if (lastVisiblePosition == -1 || (positionStart >= (msgCount - 1)
                        && lastVisiblePosition == (positionStart - 1))) {
                    recyclerView.scrollToPosition(positionStart);
                }
            }
        });

        recyclerView.setAdapter(adapter);

        adapter.startListening();
    }


    // handles the response coming back from the Google Dialogflow engine after being analysed
    private void handleResponse(AIResponse response) {

        Result result = response.getResult();
        String reply = result.getFulfillment().getSpeech();

        // set text bubble for bot
        Message chatMessageBot = new Message(reply, botName);
        ref.child("chat").push().setValue(chatMessageBot);

        // TTS
        tts.speak(reply, TextToSpeech.QUEUE_ADD, null, null);

    }


    /*
    AIListener interface implementation
     */

    @Override
    public void onResult(AIResponse response) {

        // set the text bubble for the user query
        String message = response.getResult().getResolvedQuery();
        Message chatMessageUser = new Message(message, userName);
        ref.child("chat").push().setValue(chatMessageUser);

        // handle the response, including the creation of the text bubble for the bot
        handleResponse(response);
    }

    @Override
    public void onError(AIError error) {

    }

    @Override
    public void onAudioLevel(float level) {

    }

    @Override
    public void onListeningStarted() {

    }

    @Override
    public void onListeningCanceled() {

    }

    @Override
    public void onListeningFinished() {

    }


    // Method used to change the fab button pictograms with animation
    private static void imageViewAnimatedChange(Context c,
                                               final ImageView v, final Bitmap new_image) {
        final Animation anim_out = AnimationUtils.loadAnimation(c,
                android.R.anim.fade_out);
        final Animation anim_in = AnimationUtils.loadAnimation(c,
                android.R.anim.fade_in);
        anim_out.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                v.setImageBitmap(new_image);
                anim_in.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                    }
                });
                v.startAnimation(anim_in);
            }
        });
        v.startAnimation(anim_out);
    }


    // Code that executes after a permission is granted
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_CODE) {

            for (int i = 0; i < permissions.length; i++) {

                String permission = permissions[i];
                int grantResult = grantResults[i];

                switch(permission) {
                    case Manifest.permission.ACCESS_FINE_LOCATION:
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this,"GPS permission granted",Toast.LENGTH_LONG).show();
                        }
                        else {
                            Toast.makeText(this,"GPS permission denied",Toast.LENGTH_LONG).show();
                        }
                        break;
                    case Manifest.permission.RECORD_AUDIO:
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this,"Audio recording permission granted",Toast.LENGTH_LONG).show();
                        }
                        else {
                            Toast.makeText(this,"Audio recording permission denied",Toast.LENGTH_LONG).show();
                        }
                }
            }

        }
    }


    // Cleanup Resources
    @Override
    public void onDestroy() {
        super.onDestroy();

        aiService.stopListening();
        tts.stop();
        adapter.stopListening();
    }



    /*
     AsyncTask that will complete the query to the AIDataService, remembering the result in Firebase
      */

    private class QueryTask extends AsyncTask<AIRequest,Void,AIResponse> {

        private QueryTask() {}

        @Override
        protected AIResponse doInBackground(AIRequest... aiRequests) {

            final AIRequest req = aiRequests[0];
            try {
                return aiDataService.request(req);
            } catch (AIServiceException e) {
                Log.d("EXCEPTION: ", e.getMessage());
            }
            return null;
        }
        @Override
        protected void onPostExecute(AIResponse response) {
            if (response != null) {

                handleResponse(response);
            }
        }
    }

}
