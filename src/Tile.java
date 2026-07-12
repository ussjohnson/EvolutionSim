class Tile {
  private double fertility;
  private double baseFertility;
  private double foliage;
  private Animal resident;
  public Tile(double foliage, double fertility) {
    this.foliage=foliage;
    this.fertility=fertility;
    this.baseFertility=fertility;
  }
  public Tile(double foliage) {
    this(foliage,0.2);
  }
  public Tile() {
    this(0.5);
  }

  public void recover() {
    foliage=foliage+fertility*Math.random();
    if(fertility<baseFertility) fertility=fertility+0.01*Math.random();
  }
  public double graze(double eaten) {
    if(foliage<eaten) {
      eaten=foliage;
      fertility-=0.1;
    }
    foliage-=eaten;
    return eaten;
  }
  public void fertilize(double fertilizer) {
    fertility+=fertilizer;
    if(fertility>1.0) fertility=1.0;
  }

  //Getters and Setters
  public void setFertility(double fertility) {
    this.fertility=fertility;
  }
  public double getFertility() {
    return fertility;
  }
  public void setFoliage(double foliage) {
    this.foliage=foliage;
  }
  public double getFoliage() {
    return foliage;
  }
  public void setResident(Animal resident) {
    this.resident=resident;
  }
  public Animal getResident() {
    return resident;
  }
}