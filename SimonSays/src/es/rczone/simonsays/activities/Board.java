package es.rczone.simonsays.activities;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;
import es.rczone.simonsays.R;
import es.rczone.simonsays.customviews.CustomView;
import es.rczone.simonsays.customviews.CustomViewListener;
import es.rczone.simonsays.customviews.SendView;
import es.rczone.simonsays.daos.GameDAO;
import es.rczone.simonsays.daos.MoveDAO;
import es.rczone.simonsays.model.Colors;
import es.rczone.simonsays.model.Game;
import es.rczone.simonsays.model.Game.GameStates;
import es.rczone.simonsays.model.Move;
import es.rczone.simonsays.tools.AsyncConnect;
import es.rczone.simonsays.tools.ConnectionListener;
import es.rczone.simonsays.tools.GlobalInfo;
import es.rczone.simonsays.tools.HttpPostConnector;
import es.rczone.simonsays.tools.IDialogOperations;
import es.rczone.simonsays.tools.Tools;


public class Board extends Activity implements CustomViewListener, ConnectionListener {
		
	public enum Mode{FIRST_MOVE, OPP_TURN, REPLAY_MOVE, MY_TURN};
	private String TAG = Board.class.getSimpleName();
	
	private GlobalInfo info;
	
	private CustomView roscoBlue;
	private CustomView roscoYellow;
	private CustomView roscoGreen;
	private CustomView roscoRed;
	private SendView centerButton;
	private TextView etName;
	private TextView etOppName;
	private TextView etOldThreshold;
	private TextView etNewThreshold;
	private TextView etScoreUser;
	private TextView etScoreOpp;
	
	
	private StringBuilder userMove;
	private StringBuilder moveToCheck;
	private String oppmove; 
	private Mode mode;
	private int gameID;
	private boolean guess;
//	private boolean isCheckedOppMove = false;//let me know whether the user see the opp move
//	private boolean isMatchedOppMove = false;//let me know whether the user replay the opp move  
//	private boolean isMyMoveComplete = false;
	private int threshold;
	private int oldThreshold;
	
	private HttpPostConnector post;
	private IDialogOperations sendDialog;
	private AsyncConnect connection; 

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_board);
		
		info = new GlobalInfo(this);
		
		post = new HttpPostConnector();
		
		roscoBlue = (CustomView)findViewById(R.id.roscoBlueView1);
		roscoYellow = (CustomView)findViewById(R.id.roscoYellowView1);
		roscoGreen = (CustomView)findViewById(R.id.roscoGreenView1);
		roscoRed = (CustomView)findViewById(R.id.roscoRedView1);
		centerButton = (SendView)findViewById(R.id.sendView1);
		
		etName = (TextView) findViewById(R.id.board_tv_username);
		etOppName = (TextView) findViewById(R.id.board_tv_opname);
		
		etOldThreshold = (TextView) findViewById(R.id.board_tv_oldThreshold);
		etNewThreshold = (TextView) findViewById(R.id.board_tv_newThreshold);
		etScoreUser = (TextView) findViewById(R.id.board_tv_score_user);
		etScoreOpp = (TextView) findViewById(R.id.board_tv_score_op);
		
		roscoBlue.setListener(this);
		roscoYellow.setListener(this);
		roscoGreen.setListener(this);
		roscoRed.setListener(this);
		centerButton.setListener(this);
		
		userMove = new StringBuilder();
		moveToCheck = new StringBuilder();

		
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			
			gameID = extras.getInt(info.KEY_GAME_ID);
			prepareGame(gameID);
		}
		
	}
	
	private void prepareGame(int gameID){
		
		etName.setText(" "+info.USERNAME);
    	
		Game item = new GameDAO().get(gameID);
		
		switch(item.getState()){
		case FINISHED:
			break;
		case FIRST_MOVE:
			mode = Mode.FIRST_MOVE;
			threshold = item.getNumMoves();
			oldThreshold = threshold;
//			isCheckedOppMove = true;
//			isMatchedOppMove = true;
			oppmove ="";
			guess = false;//first time is like a fail
			centerButton.setStateHand();
			break;
		case IN_PROGRESS:
			mode = Mode.OPP_TURN;
			threshold = item.getNumMoves();
			oldThreshold = threshold==4? 4: threshold-1 ;
			Move m = new MoveDAO().getMoveOfGame(gameID);
			oppmove = m.getMove();
			Toast.makeText(this, "Check the opponent's move", Toast.LENGTH_SHORT).show();
//			isCheckedOppMove = false;
//			isMatchedOppMove = false;
			centerButton.setStateEye();
			break;
		
		default:
			break;
		
		}
		
		
		etOppName.setText(" "+item.getOpponentName());	
		etScoreUser.setText(""+item.getUserScore());
		etScoreOpp.setText(""+item.getOppScore()); 
		
		etOldThreshold.setText("Opp threshold: "+oldThreshold);
		etNewThreshold.setText("User threshold: "+threshold);
		
		sendDialog = new IDialogOperations() {
			@Override
			public void positiveOperation() {
				connection = new AsyncConnect(Board.this);
				connection.execute(""+Board.this.gameID,info.USERNAME,userMove.toString(),guess?info.RIGHT_MATCH_MOVE:info.FAIL_MATCH_MOVE);
			}
			@Override
			public void negativeOperation() {/*Nothing to do*/}
		};
			
		
		
	}
	
	@Override
	protected void onDestroy(){
		super.onDestroy();
		setEnableFalseClick(false);
	}

	/**
	 * Method to listen custom views
	 */
	@Override
	public void onClicked(CustomView view) {
		
		switch(view.getID()){
		
			case R.id.roscoBlueView1:
				Log.d(TAG, "blue");
				proccessColor(Colors.BLUE);
				break;
			
			case R.id.roscoYellowView1:
				Log.d(TAG, "yellow");
				proccessColor(Colors.YELLOW);
				break;
				
			case R.id.roscoGreenView1:
				Log.d(TAG, "green");
				proccessColor(Colors.GREEN);
				break;
				
			case R.id.roscoRedView1:
				Log.d(TAG, "red");
				proccessColor(Colors.RED);
				break;
				
			case R.id.sendView1:
				pressCenterButton();
				Log.d(TAG, "send");
				break;
		}
		
	}
	
	
	private void pressCenterButton() {

		switch(mode){
		case FIRST_MOVE:
		case MY_TURN:
			{
				switch(centerButton.getState()){
				case HAND:
					Toast.makeText(this, "You should make your move!", Toast.LENGTH_SHORT).show();
					break;
				case RESET:
					resetMove();
					Toast.makeText(this, "Your move has been reset", Toast.LENGTH_SHORT).show();
					centerButton.setStateHand();
					break;
				case SEND:
					Tools.askConfirmation(this, "Confirm send", "Do you want to send the move?", 
					R.drawable.send_icon_confirm, "Yes", "No", sendDialog);
					break;
				
				default:
					break;
				
				}
				
			}
			break;
			
		case OPP_TURN:
			{
				switch(centerButton.getState()){
				case EYE:
					showMove(oppmove);
					break;
				
				default:
					break;
				
				}

			}
			
		case REPLAY_MOVE:
			{
				switch(centerButton.getState()){
				case HAND:
					Toast.makeText(this, "Now you have to replay the opponent's move", Toast.LENGTH_SHORT).show();
					break;
				case RESET:
					resetMove();
					Toast.makeText(this, "Your move has been reset", Toast.LENGTH_SHORT).show();
					centerButton.setStateHand();
					break;
				case TIC:
					if(matchMoves())
						Toast.makeText(this, "You score.", Toast.LENGTH_SHORT).show();
					else
						Toast.makeText(this, "Your fail", Toast.LENGTH_SHORT).show();
					mode=Mode.MY_TURN;
					centerButton.setStateHand();
					break;
					
				default:
					break;
				
				}
	
			}
		
		default:
			break;
					
		}
		
		
	}

	
	private boolean matchMoves(){
		
		String[] colorsOpp = oppmove.split("-");
		String[] colorsCheck = moveToCheck.toString().split("-");
		//isCheckedOppMove = true;
	
		return Arrays.equals(colorsOpp, colorsCheck);
		
	}
	
	private void showMove(String oppmove) {
		
		String[] colors = oppmove.split("-");
		int it=0;
		final long duration = 300;
		final long accuracy = 150;//closer to 0 means more accuracy
		
		int lenght = colors.length;
		
		for(String color : colors){
			mode = Mode.OPP_TURN;
			setEnableFalseClick(true);
			Colors enumColor = Colors.values()[Integer.parseInt(color)];
			Handler handler = new Handler();
			switch (enumColor) {
			case BLUE:
				 	handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							MotionEvent event = MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN,0,0,0);
							roscoBlue.onTouchEvent(event);
						}
					}, (duration*it)+accuracy);
				 
				 	handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							roscoBlue.onTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,0,0,0));
						}
					}, duration*(it+1));
				 
			    
				break;
			case GREEN:
				
					handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						MotionEvent event = MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN,0,0,0);
						roscoGreen.onTouchEvent(event);
					}
					}, (duration*it)+accuracy);
			 
					handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							roscoGreen.onTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,0,0,0));
						}
					}, duration*(it+1));
					
				break;
			case RED:
				
					handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						MotionEvent event = MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN,0,0,0);
						roscoRed.onTouchEvent(event);
					}
					}, (duration*it)+accuracy);
			 
					handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							roscoRed.onTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,0,0,0));
						}
					}, duration*(it+1));
					
				break;
			case YELLOW:
				
					handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						MotionEvent event = MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN,0,0,0);
						roscoYellow.onTouchEvent(event);
					}
					}, (duration*it)+accuracy);
			 
					handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						roscoYellow.onTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,0,0,0));
					}
					}, duration*(it+1));
					
				break;
			
			}
			
			it++;
			if(lenght==it){
				
				handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						setEnableFalseClick(false);
						centerButton.setStateHand();
						mode = Mode.REPLAY_MOVE;
						resetMove();
						//isCheckedOppMove = true;
						Toast.makeText(Board.this, "Now you have to reproduce the same succession of colors in the board", Toast.LENGTH_SHORT).show();
					}
				}, (duration*it)+accuracy);
				
			}
		
		}
		
	}
	
	
	private void setEnableFalseClick(boolean b){
		roscoBlue.enableFalseClick(b);
		roscoYellow.enableFalseClick(b);
		roscoGreen.enableFalseClick(b);
		roscoRed.enableFalseClick(b);
		centerButton.enableFalseClick(b);
	}

	private void proccessColor(Colors color){
		
		switch(mode){
		case FIRST_MOVE:
		case MY_TURN:
			userMove.append(color.ordinal()+"-");
			if(userMove.length()==threshold*2){
				Toast.makeText(this, "You reach the threshold", Toast.LENGTH_SHORT).show();
				//isMyMoveComplete=true;
				centerButton.setStateSend();
			}
			else if (userMove.length()>threshold*2){
				Toast.makeText(this, "You should not exceed the threshold", Toast.LENGTH_SHORT).show();
				//isMyMoveComplete=false;
				centerButton.setStateReset();
			}
			else{
				centerButton.setStateReset();
			}
			
			break;
		case REPLAY_MOVE:
			
			moveToCheck.append(color.ordinal()+"-");
			if(moveToCheck.length()==oldThreshold*2){
				Toast.makeText(this, "You reach the threshold", Toast.LENGTH_SHORT).show();
				centerButton.setStateTic();
			}
			else if (moveToCheck.length()>threshold*2){
				Toast.makeText(this, "You should not exceed the opponent threshold", Toast.LENGTH_SHORT).show();
				centerButton.setStateReset();
			}
			else{
				centerButton.setStateReset();
			}
			
			break;
		case OPP_TURN:
			return;
		default:
			break;
		
		}

		
	}
	
	
	/**
	 * Clears the buffers.
	 */
	private void resetMove(){
		userMove.setLength(0);
		moveToCheck.setLength(0);
	}
	
	

	@Override
	public boolean inBackground(String... params) {
		ArrayList<NameValuePair> postParametersToSend = new ArrayList<NameValuePair>();

		postParametersToSend.add(new BasicNameValuePair("game_id", params[0]));
		postParametersToSend.add(new BasicNameValuePair("player_name", params[1]));
		postParametersToSend.add(new BasicNameValuePair("move", params[2]));
		postParametersToSend.add(new BasicNameValuePair("guess", params[3]));

		// realizamos una peticion y como respuesta obtenes un array JSON
		JSONArray jdata = post.getserverdata(postParametersToSend, HttpPostConnector.URL_MAKE_A_MOVE);


		// si lo que obtuvimos no es null, es decir, hay respuesta v�lida
		if (jdata != null && jdata.length() > 0) {

			try {
				JSONObject json_data = jdata.getJSONObject(0);
				String codeFromServer = json_data.getString("code");
				//String messageFromServer = json_data.getString("message");
				
				if(codeFromServer.equals("400")){
					Game g = new GameDAO().get(gameID);
					g.setState(GameStates.WAITING_FOR_MOVE);
					new GameDAO().update(g);
					return true;
				}
				else{
					return false;
				}
				
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			return true;
		} else { // json obtenido invalido verificar parte WEB.
			Log.e("JSON  ", "ERROR");
			return false;
		}
	}

	@Override
	public boolean validateDataBeforeConnection(String... params) {
		//FIXME
		return true;
	}

	@Override
	public void afterGoodConnection() {
		Toast.makeText(this, "The move has been sent", Toast.LENGTH_SHORT).show();
		finish();
		
	}

	@Override
	public void invalidInputData() {
		Toast.makeText(this, "The move has not been sent", Toast.LENGTH_SHORT).show();
		
	}

	@Override
	public void afterErrorConnection() {
		Toast.makeText(this, "Problem connections. The move has not been sent.", Toast.LENGTH_SHORT).show();
		
	}
}
