package pe.manchapp.beta;

final class GuidedMaskSeed {
    private GuidedMaskSeed(){}
    static boolean inside(int width,int height,int x,int y,float nx,float ny){
        double radius=Math.max(6,Math.min(width,height)/12.0),dx=x-nx*width,dy=y-ny*height;
        return dx*dx+dy*dy<=radius*radius;
    }
    static boolean outer(int width,int height,int x,int y){
        int band=Math.max(3,Math.round(Math.min(width,height)*.08f));
        return x<band||y<band||x>=width-band||y>=height-band;
    }
}
