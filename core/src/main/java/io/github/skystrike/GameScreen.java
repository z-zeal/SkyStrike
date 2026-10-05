package io.github.skystrike;
import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.OrthographicCamera;
import io.github.skystrike.shared.map.*;
public final class GameScreen implements Screen {
    private final OrthographicCamera camera=new OrthographicCamera();
    private final ShapeRenderer shapes=new ShapeRenderer();
    private final ArenaMap map=ArenaMap.standard();
    public void show(){
        camera.position.set(1500,1000,0);
        camera.setToOrtho(false,1500,1000);
    }
    public void render(float delta){
        if(Gdx.input.isKeyPressed(Input.Keys.LEFT))camera.position.x-=500*delta;
        if(Gdx.input.isKeyPressed(Input.Keys.RIGHT))camera.position.x+=500*delta;
        if(Gdx.input.isKeyPressed(Input.Keys.UP))camera.position.y+=500*delta;
        if(Gdx.input.isKeyPressed(Input.Keys.DOWN))camera.position.y-=500*delta;
        camera.update();
        Gdx.gl.glClearColor(.035f,.045f,.07f,1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.22f,.28f,.36f,1);
        for(Rect r:map.solids())shapes.rect(r.x(),r.y(),r.width(),r.height());
        shapes.end();
    }
    public void resize(int w,int h){
        if(w>0&&h>0)camera.viewportWidth=1500;
        camera.viewportHeight=1000;
    }
    public void pause(){
    }
    public void resume(){
    }
    public void hide(){
    }
    public void dispose(){
        shapes.dispose();
    }
}
