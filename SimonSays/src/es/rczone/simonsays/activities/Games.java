package es.rczone.simonsays.activities;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;
import es.rczone.simonsays.R;
import es.rczone.simonsays.activities.fragments.FragmentGamesList;
import es.rczone.simonsays.activities.fragments.listeners.ListListener;
import es.rczone.simonsays.controllers.GamesController;
import es.rczone.simonsays.daos.GameDAO;
import es.rczone.simonsays.model.Game;
import es.rczone.simonsays.model.Game.GameStates;
import es.rczone.simonsays.tools.AsyncConnect;
import es.rczone.simonsays.tools.ConnectionListener;
import es.rczone.simonsays.tools.GlobalInfo;
import es.rczone.simonsays.tools.HttpPostConnector;
import es.rczone.simonsays.tools.IDialogOperations;
import es.rczone.simonsays.tools.Tools;

public class Games extends FragmentActivity implements Handler.Callback, ListListener<Game>, ConnectionListener{

	
	
	//private enum Connections{ACCEPT_REQUEST,REJECT_REQUEST}
	private GlobalInfo info;
	
	private FragmentGamesList frg_rdy_games_list;
	private FragmentGamesList frg_waiting_games_list;
	private GamesController controller;
	private List<Game> games_rdy_list;
	private List<Game> games_waiting_list;
	private boolean listsUpdated;
	private IDialogOperations responseRequestGame;
	private AsyncConnect connection;
	//private Connections connectionType;
	private HttpPostConnector post;
	private Game game;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.fragment_activity_games);
        
        info = new GlobalInfo(this);
 
        frg_rdy_games_list =(FragmentGamesList)getSupportFragmentManager().findFragmentById(R.id.frg_game_ready_list);
        frg_rdy_games_list.setListener(this);
        frg_waiting_games_list = (FragmentGamesList)getSupportFragmentManager().findFragmentById(R.id.frg_game_waiting_list);
        frg_waiting_games_list.setListener(this);
        
        games_rdy_list = new ArrayList<Game>();
        games_waiting_list = new ArrayList<Game>();
        
        controller = new GamesController(games_rdy_list, games_waiting_list);
		
		controller.addOutboxHandler(new Handler(this));
		
		listsUpdated = false;
		
		post = new HttpPostConnector();
		prepareDiologs();
		
         
	}
	
	private void prepareDiologs() {
		responseRequestGame = new IDialogOperations() {
			
			@Override
			public void positiveOperation() {
				connection = new AsyncConnect(Games.this);
				connection.execute(""+game.getID(),info.ACCEPT_REQUEST,info.USERNAME);
			}
			
			@Override
			public void negativeOperation() {
				connection = new AsyncConnect(Games.this);
				connection.execute(""+game.getID(),info.REJECT_REQUEST,info.USERNAME);				
			}
		};
		
	}

	@Override
	protected void onResume(){
		super.onResume();
		if(games_rdy_list!=null && games_waiting_list!=null && !listsUpdated){
			controller.handleMessage(GamesController.MESSAGE_GET_READY_LIST);
			controller.handleMessage(GamesController.MESSAGE_GET_WAITING_LIST);
			listsUpdated = true;
		}
		
	}
	
	
	@Override
	protected void onPause(){
		super.onPause();
		listsUpdated=false;
	}
	

	@Override
	public void onItemClicked(Game item) {
		
		game = item;

		switch(item.getState()){
		
		case PENDING:
			Tools.askConfirmation(this, "Game request", "You have a game request.", 
			R.drawable.send_icon_confirm, "Accept", "Reject", responseRequestGame);
			break;
		
		case IN_PROGRESS:
		case FIRST_MOVE:	
			Intent intent = new Intent(this, Board.class);
        	intent.putExtra(info.KEY_GAME_ID, item.getID());
        	startActivity(intent);
			break;
		case REFUSED:
			break;
		case WAITING_FOR_MOVE:
			break;
		case WAITING_FOR_RESPONSE:
			break;
		case FINISHED:
			break;
		default:
			break;
		
		}
		
	}

	@Override
	public void onItemLongClicked(Game item) {
		// xxx
//		item.setState(GameStates.WAITING_FOR_MOVE);
//		new GameDAO().update(item);
		
	}

	@Override
	public boolean handleMessage(Message message) {
		
		switch(message.what) {
			case GamesController.MESSAGE_TO_VIEW_READY_MODEL_UPDATED:
				this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						frg_rdy_games_list.refreshList(games_rdy_list);
					}
				});
				return true;
			
			case GamesController.MESSAGE_TO_VIEW_WAITING_MODEL_UPDATED:
				this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						frg_waiting_games_list.refreshList(games_waiting_list);
					}
				});
				return true;
		}
		return false;
	}

	@Override
	public boolean inBackground(String... params) {
		ArrayList<NameValuePair> postParametersToSend = new ArrayList<NameValuePair>();
		postParametersToSend.add(new BasicNameValuePair("game_id", params[0]));
		postParametersToSend.add(new BasicNameValuePair("response", params[1]));
		postParametersToSend.add(new BasicNameValuePair("player_name", params[2]));
		

		// realizamos una peticion y como respuesta obtenes un array JSON
		JSONArray jdata = post.getserverdata(postParametersToSend, HttpPostConnector.URL_RESPONSE_REQUEST_GAME);


		// si lo que obtuvimos no es null, es decir, hay respuesta v�lida
		if (jdata != null && jdata.length() > 0) {

			try {
				JSONObject json_data = jdata.getJSONObject(0);
				String codeFromServer = json_data.getString("code");
				//String messageFromServer = json_data.getString("message");
				
				if("300".equals(codeFromServer)){
					
					GameDAO dao = new GameDAO();
					Game game = dao.get(Integer.parseInt(params[0]));
					game.setState(GameStates.WAITING_FOR_MOVE);
					dao.update(game);
					connection.attachMessage("You have accepted the request game.");
					return true;
				}
				else if("301".equals(codeFromServer)){
					GameDAO dao = new GameDAO();
					Game game = dao.get(Integer.parseInt(params[0]));
					game.setState(GameStates.REFUSED);
					dao.update(game);
					connection.attachMessage("You have rejected the request game.");
					return true;
				}
				else return false;
				
			

			} catch (JSONException e) {
				//Toast.makeText(this, "Error desconocido.", Toast.LENGTH_SHORT).show();
				return false;
			}
			
			
		} else { // json obtenido invalido verificar parte WEB.
			Log.e("JSON  ", "ERROR");
			//Toast.makeText(this, "La conexi�n ha fallado. No se ha completado el registro.", Toast.LENGTH_SHORT).show();
			return false;
		}
	}

	@Override
	public boolean validateDataBeforeConnection(String... params) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public void afterGoodConnection() {
		Toast.makeText(this, connection.getMessage(), Toast.LENGTH_SHORT).show();
	}


	@Override
	public void invalidInputData() {
		Toast.makeText(this, "There was a problem.", Toast.LENGTH_SHORT).show();
		
	}


	@Override
	public void afterErrorConnection() {
		Toast.makeText(this, "There was a problem.", Toast.LENGTH_SHORT).show();
		
	}

}
