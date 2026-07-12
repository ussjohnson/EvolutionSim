class Gene {
  private double value;
  private double max;
  private double min;
  private double variation;
  private String name;
  
  public Gene(double max) {
    this(max, 0.0);
  }
  public Gene(double max, double min) {
    this(max, min, (max-min)/10.0);
  }
  public Gene(double max, double min, double variation) {
    this(max, min, variation, (max-min)/2.0);    
  }
  public Gene(double max, double min, double variation, double value) {
    this("unknown",max,min,variation,value);
  }
  public Gene(String name, double max, double min, double variation, double value) {
    this.name=name;
    this.max=max;
    this.min=min;
    this.variation=variation;
    this.value=value;
  }
  public Gene(double[] inputs) {
    this(inputs[0],inputs[1],inputs[2],inputs[3]);
  }
  public Gene(String name, double[] inputs) {
    this(inputs);
    this.name=name;
  }

  public void mutate() {
    double change = (Math.random()*variation)-variation/2.0;
    value+=change;
  }
  public int intScaleOffset(int upper, int lower) {
    int original = convToIntScale(upper,lower);
    int offset = (upper-lower)/2;
    if(original>(lower+offset)) return original-offset;
    return original+offset;
  }
  public int convToIntScale(int upper, int lower) {
    Double interimDouble=Double.valueOf(convToDoubleScale(upper, lower));
    return interimDouble.intValue();
  }
  public double convToDoubleScale(double upper, double lower) {
    double percentile=(value-min)/(max-min);
    return percentile*(upper-lower)+lower;
  }
  
  @Override
  public String toString() {
    return name + ": " + value + " (" + min + "-" + max + ")";
  }
  
  //Getters and Setters
  public double value() {
  	return value;
  }  
  public void setValue(double value) {
    if(value>this.max) value=this.max;
    if(value<this.min) value=this.min;
    this.value = value;
  }  
  public double getMax() {
  	return max;
  }  
  public void setMax(double max) {
  	this.max = max;
  }  
  public double getMin() {
  	return min;
  }  
  public void setMin(double min) {
  	this.min = min;
  }  
  public double getVariation() {
  	return variation;
  }  
  public void setVariation(double variation) {
  	this.variation = variation;
  }
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }
  
}