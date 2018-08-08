package com.example.uia93237.chatbot;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import ai.api.AIListener;
import ai.api.AIServiceException;
import ai.api.RequestExtras;
import ai.api.android.AIConfiguration;
import ai.api.android.AIDataService;
import ai.api.android.AIService;
import ai.api.model.AIContext;
import ai.api.model.AIError;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.ResponseMessage;
import ai.api.model.Result;



public class MainActivity extends AppCompatActivity implements AIListener {


    // Identifier of the chat instance on the Firebase DB - obtained through UUID
    private static String uniqueID = null;
    private static final String PREF_UNIQUE_ID = "PREF_UNIQUE_ID";

    private final static String accessToken = "f71f8955cc7a4003995542bb46f4f9e4"; // Connecting to a specific Google Dialogflow Agent

    private final static String[] permissions = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
    };

    private final static String botName = "Agnes";
    private final static String userName = "User";

    private final static int PERMISSION_CODE = 1;

    private EditText editText;

    // Dialog which prompts for internet connection
    private Dialog dialog;

    // TTS engine
    private TextToSpeech tts;

    // Firebase DB
    private DatabaseReference ref;

    // Firebase Recycler Adapter
    private FirebaseRecyclerAdapter<Message, ChatViewHolder> adapter;

    // Dialogflow service
    private AIService aiService;
    private AIDataService aiDataService;

    // Location related instances
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private Map<String, String> locationMap;

    // boolean used to switch between the 2 pictograms in the fab button
    private boolean flagFab = true;


    /*
    Main Activity methods
     */

    @Override
    protected void onCreate(Bundle savedInstanceState){

        super.onCreate(savedInstanceState);

        // Request: Record Audio and Location Permission
        checkPermissions();

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
        tts = new TextToSpeech(this, (status) -> {
            if(status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.UK);
            }
        });


        // Set up location related instances
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationMap = new HashMap<>();
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    Location location = locationResult.getLastLocation();
                    locationMap.put("latitude", String.valueOf(location.getLatitude()));
                    locationMap.put("longitude", String.valueOf(location.getLongitude()));
                }
            }
        };


        // Set up Firebase
        ref = FirebaseDatabase.getInstance().getReference();
        ref.keepSynced(true);
        setupDBCleanupListener();

        // Set up Dialogflow
        final AIConfiguration config =
                new AIConfiguration(accessToken, AIConfiguration.SupportedLanguages.English, AIConfiguration.RecognitionEngine.System);

        // Used for voice search
        aiService = AIService.getService(this, config);
        aiService.setListener(this);

        // Used for text search
        aiDataService = new AIDataService(this, config);

        // Inititialize request to DialogFlow
        final AIRequest aiRequest = new AIRequest();
        

        // Set up send message button listener
        addBtn.setOnClickListener(view -> {

            view.playSoundEffect(android.view.SoundEffectConstants.CLICK);

            // Get message from the editor
            String message = editText.getText().toString().trim();

            // Set location related context and request extras
            AIContext locationCtx = new AIContext("location");
            locationCtx.setParameters(locationMap);
            RequestExtras requestExtras = new RequestExtras(Collections.singletonList(locationCtx), null);

            if (!message.equals("")) {

                Message chatMessage = new Message(message, userName);
                ref.child(id(this)).push().setValue(chatMessage);
                aiRequest.setQuery(message);

                new QueryTask(requestExtras).execute(aiRequest);

            }
            else { // the else block takes care of voice input
                aiService.startListening(requestExtras);
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
                        .setQuery(ref.child(id(this)), Message.class)
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


    @Override
    protected void onResume() {
        super.onResume();

        internetCheckOrClose();
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopLocationUpdates();
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
    Secondary methods
     */


    // handles the response coming back from the Google Dialogflow engine after being analysed
    private void handleResponse(AIResponse response) {

        boolean usedTTS = false;
        StringBuilder fullReply = new StringBuilder();

        Result result = response.getResult();
        List<ResponseMessage> messages = result.getFulfillment().getMessages();

        for (ResponseMessage message : messages) {

            if (message instanceof ResponseMessage.ResponseSpeech) {

                String reply = ((ResponseMessage.ResponseSpeech) message).getSpeech().get(0);
                fullReply.append('\n').append(reply);

                // TTS
                if (!usedTTS) {
                    tts.speak(sanitizeForTTS(reply), TextToSpeech.QUEUE_ADD, null, null);
                    usedTTS = true;
                }

            }
        }
        fullReply.deleteCharAt(0);

        // set text bubble for bot
        Message chatMessageBot = new Message(fullReply.toString(), botName);
        ref.child(id(this)).push().setValue(chatMessageBot);
    }

    /*
    Utility methods
     */

    // Requests permissions if not already granted
    private void checkPermissions() {

        for (String perm : permissions) {

            int grant = this.checkCallingOrSelfPermission(perm);
            if (! (grant == PackageManager.PERMISSION_GRANTED)) {
                ActivityCompat.requestPermissions(this, new String[]{perm}, PERMISSION_CODE);
            }
        }

    }


    // make string ready for TTS by removing emojis
    private String sanitizeForTTS(String s) {

        //Character.UnicodeScript.of () was not added till API 24 so this is a 24 up solution
        if (Build.VERSION.SDK_INT > 23) {
            // this is where we will store the reassembled string
            StringBuilder result = new StringBuilder();
            /*
            we are going to cycle through the word checking each character
            to find its unicode script to compare it against known alphabets
              */
            for (int i = 0; i < s.length(); i++) {
                // currently emojis don't have a devoted unicode script so they return UNKNOWN
                if (!Character.UnicodeScript.of(s.charAt(i)).toString().equals("UNKNOWN")) {
                    result.append(s.charAt(i));
                }
            }

            return result.toString();
        }

        // if API <= 23 the string cannot be sanitized properly
        return s;
    }


    // Method used to assign an ID per installation of App. This will uniquely identify a chat instance
    private synchronized String id(Context context) {

        if (uniqueID == null) {
            SharedPreferences sharedPrefs = context.getSharedPreferences(
                    PREF_UNIQUE_ID, Context.MODE_PRIVATE);
            uniqueID = sharedPrefs.getString(PREF_UNIQUE_ID, null);
            if (uniqueID == null) {
                uniqueID = UUID.randomUUID().toString();
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString(PREF_UNIQUE_ID, uniqueID);
                editor.apply();
            }
        }
        return uniqueID;
    }

    // Shows required connection dialog if the internet is unavailable
    private void internetCheckOrClose() {

        if (!isInternetAvailable()) {
            showConnectionDialog();
        }
    }

    // Pings google.com to check for internet connection
    private boolean isInternetAvailable() {
        InetAddress inetAddress = null;
        try {
            Future<InetAddress> future = Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    return InetAddress.getByName("google.com");
                } catch (UnknownHostException e) {
                    return null;
                }
            });

            inetAddress = future.get(3, TimeUnit.SECONDS);
            future.cancel(true);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Log.d("CONNECTION PROBLEM: ", "Internet not available!");
        }

        return inetAddress != null && !inetAddress.toString().equals("");
    }

    // Shows dialog which prompts user to enable internet connection
    private void showConnectionDialog() {
        if (dialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Agnes needs Internet Connection!")
                    .setCancelable(false)
                    .setPositiveButton("Connect to Internet", (dialog, id) -> startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)))
                    .setNegativeButton("Quit", (dialog, id) -> this.finish());

            dialog = builder.show();
        }
        else if (!dialog.isShowing()) {
            dialog.show();
        }
    }

    // Requests location updates from the FusedLocationClient
    private void startLocationUpdates() {

        try {
            mFusedLocationClient.requestLocationUpdates(LocationRequest.create(), mLocationCallback, null);
        } catch (SecurityException e) {
            Log.d("LOCATION UNAVAILABLE: ", "Location service is not available!");
        }
    }

    // Stops the location updates
    private void stopLocationUpdates() {

        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }




    /*
    AIListener interface implementation
     */

    @Override
    public void onResult(AIResponse response) {

        // set the text bubble for the user query
        String rawMessage = response.getResult().getResolvedQuery();
        String capitalisedMessage = rawMessage.substring(0, 1).toUpperCase() + rawMessage.substring(1);
        Message chatMessageUser = new Message(capitalisedMessage, userName);
        ref.child(id(this)).push().setValue(chatMessageUser);

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


    // Listener which cleans-up database entries older than 1 day
    private void setupDBCleanupListener() {
        long cutoff = new Date().getTime() - TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);
        Query oldItems = ref.child(id(this)).orderByChild("timestamp").endAt(cutoff);
        oldItems.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot itemSnapshot: snapshot.getChildren()) {
                    itemSnapshot.getRef().removeValue();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                throw databaseError.toException();
            }
        });
    }



    /*
     AsyncTask that will complete the query to the AIDataService, remembering the result in Firebase
      */

    private class QueryTask extends AsyncTask<AIRequest,Void,AIResponse> {

        private RequestExtras extras;

        private QueryTask(RequestExtras extras) {
            this.extras = extras;
        }

        @Override
        protected AIResponse doInBackground(AIRequest... aiRequests) {

            final AIRequest req = aiRequests[0];
            try {
                return aiDataService.request(req, extras);
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
