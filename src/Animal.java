class Animal {
  private Gene[] genes;
  private int color;
  private int size=0;
  private int diet=1;
  private int speed=2;
  private int tolerance=3;
  private double metabolism;
  private double reproReqs;
  private String name;

  public Animal(double sizeIn, double dietIn, double speedIn, double toleranceIn, Animal base) {
    this(base.getGenes());
    genes[size].setValue(sizeIn);
    genes[diet].setValue(dietIn);
    genes[speed].setValue(speedIn);
    genes[tolerance].setValue(toleranceIn);
    name=calcName();
    color=calcColor();
  }
  public Animal(Gene[] genesIn) {
    this.genes=genesIn;
    name=calcName();
    color=calcColor();
  }
  public Animal(double[][] genesIn, String[] namesIn) {
    this.genes=new Gene[genesIn.length];
    for(int i=0; i<genes.length; i++) {
      genes[i]=new Gene(namesIn[i],genesIn[i]);
    }
    name=calcName();
    color=calcColor();
  }

  public Animal reproduce() {
    Animal baby=this;
    for(int i=0; i<genes.length; i++) {
      genes[i].mutate();
    }
    return baby;
  }
  
  public int calcColor() {
    return 0;
  }
  private String calcName() {
    String symbols="12345678";
    String vowels="aeiouy";
    String blenders="wrln";
    String anyCons="tpdfghjkzxcvbm0124";
    String notLast="37s";
    String notFirst="x56";
    String[] digraphs={"sh","ch","th","wr","ph","ss","ck","qu"};
    String pool=vowels + blenders + anyCons + notLast;
    int poolSize=pool.length();
    int numGenes=genes.length;
    int index=genes[0].convToIntScale(0,poolSize);
    //System.out.println(pool);
    //System.out.println(poolSize);
    //System.out.println(index);
    String name=String.valueOf(pool.charAt(index));
    if(symbols.contains(name)) name=digraphs[symbols.indexOf(name)];
    //System.out.println(name);
    for(int i=1;i<numGenes*2;i++) {
      String prevChar=String.valueOf(name.charAt(i-1));
      if(vowels.contains(prevChar) || (i>1 && blenders.contains(prevChar) && vowels.contains(String.valueOf(name.charAt(i-2))))) {
        pool=blenders+anyCons+notFirst;
        if(i<numGenes-1) pool=pool.concat(notLast);
      }
      else pool=vowels;
      poolSize=pool.length();
      int geneIndex=i%numGenes;
      index=genes[geneIndex].convToIntScale(0,poolSize);
      if(i>=numGenes) index=genes[geneIndex].intScaleOffset(0,poolSize);
      //System.out.println(pool);
      //System.out.println(poolSize);
      //System.out.println(index);
      String next=String.valueOf(pool.charAt(index));
      if(symbols.contains(next)) next=digraphs[symbols.indexOf(next)];
      name=name.concat(next);
      //System.out.println(name);
    }
    
    name=name.substring(0,1).toUpperCase() + name.substring(1);
    return name;
  }

  @Override
  public String toString() {
    String description=name;
    for(int i=0;i<genes.length;i++) {
      description=description.concat("\n"+ genes[i]);
    }
    description=description.concat("\nMetabolism: " + metabolism + "\nReproductive Requirements: " + reproReqs);
    return description;
  }

  //Getters and Setters
  public int getColorInt() {
    return color;
  }
  
  public String getColorHex() {
    return Integer.toHexString(color);
  }
  
  public Gene getSize() {
  	return genes[size];
  }
  
  private void setSize(Gene newSize) {
  	genes[size] = newSize;
  }
  
  public Gene getSpeed() {
  	return genes[speed];
  }
  
  private void setSpeed(Gene newSpeed) {
  	genes[speed] = newSpeed;
  }
  
  public Gene getDiet() {
  	return genes[diet];
  }
  
  private void setDiet(Gene newDiet) {
  	genes[diet] = newDiet;
  }
  
  public double getMetabolism() {
  	return metabolism;
  }
  
  public Gene getTolerance() {
  	return genes[tolerance];
  }
  
  private void setTolerance(Gene newTolerance) {
  	genes[tolerance] = newTolerance;
  }
  
  public double getReproReqs() {
  	return reproReqs;
  }
  
  private void setReproReqs(double reproReqs) {
  	this.reproReqs = reproReqs;
  }
  
  public String getName() {
  	return name;
  }

  public Gene[] getGenes() {
    return genes;
  }
  
}