class Main {
  public static void main(String[] args) {
    String[] geneNames={"Size", "Diet", "Speed", "Tolerance"};
    Gene[] geneSet=new Gene[geneNames.length];
    for(int i=0; i<geneSet.length; i++) {
      double max=Math.random();
      double min=Math.random()*max;
      double variation=(max-min)/10.0;
      double value=Math.random()*(max-min)+min;
      double[] inputs={max,min,variation,value};
      geneSet[i]=new Gene(geneNames[i],inputs);
    }
    Animal test = new Animal(geneSet);
    System.out.println(test.toString());
  }
}