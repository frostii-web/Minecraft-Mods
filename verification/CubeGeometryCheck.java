import dev.frostii.irg.client.CubeGeometry;
import java.util.*;

public final class CubeGeometryCheck {
    public static void main(String[] args) {
        double[][] views={{0,0,3},{Math.PI,0,2},{Math.PI/2,0,4},{-Math.PI/2,0,5},{0,Math.PI/2,1},{0,-Math.PI/2,0}};
        int checks=0;
        for(double[] view:views) {
            var faces=CubeGeometry.faces(view[0],view[1],100,100,100);
            if(CubeGeometry.pick(faces,100,100)!=(int)view[2])throw new AssertionError("World direction mapping failed");
            checks++;
        }
        Random random=new Random(110);
        Set<Integer> reached=new HashSet<>();
        for(int n=0;n<2000;n++) {
            var faces=CubeGeometry.faces(random.nextDouble()*Math.PI*2,random.nextDouble()*Math.PI*2,100,100,100);
            if(CubeGeometry.pick(faces,-100,-100)!=-1)throw new AssertionError("Background selected a face");
            for(var face:faces) {
                double x=0,y=0;
                for(var p:face.points()){x+=p[0]/4;y+=p[1]/4;}
                if(CubeGeometry.pick(faces,x,y)!=face.direction())throw new AssertionError("Visible face picked the wrong world direction");
                reached.add(face.direction());checks++;
            }
        }
        if(reached.size()!=6)throw new AssertionError("Cannot reach all six directions");
        System.out.println("CubeGeometry: "+checks+" face checks passed; all six world directions reachable.");
    }
}
